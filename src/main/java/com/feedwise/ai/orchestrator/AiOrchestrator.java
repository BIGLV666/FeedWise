package com.feedwise.ai.orchestrator;

import com.feedwise.ai.AiSchemaValidator;
import com.feedwise.ai.tool.ToolResult;
import com.feedwise.ai.tool.ToolContext;
import com.feedwise.ai.llm.AiProperties;
import com.feedwise.ai.llm.AiUnavailableException;
import com.feedwise.ai.llm.LlmClient;
import com.feedwise.ai.tool.ToolRegistry;
import com.feedwise.entity.Feedback;
import com.feedwise.mapper.FeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 编排器：把"模型只能调用受限工具"落到执行层。
 *
 * <p>流程：收集输入 → 构造提示词 → 循环调用 LlmClient（最多 maxToolCalls 次工具执行）
 * → 每个工具调用先过 Schema 校验再过业务约束（工具实现内）→ 结果回填对话继续。
 * 模型不可用时抛 {@link AiUnavailableException}，调用方降级为手工流程，
 * 已有业务数据不受影响（工具调用全部失败即无副作用）。</p>
 */
@Component
public class AiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrator.class);

    private static final String SYSTEM_PROMPT = """
            你是企业报销软件的产品助理。你只能通过调用系统提供的受限工具来整理用户反馈：
            1. 只能处理输入中给出的脱敏反馈，不得虚构反馈内容；
            2. 只有表达了同一个功能模块下同一个问题的反馈才能放进同一张候选卡片；
               含义不同（现象不同）的反馈必须拆成多张卡片，即使关键词相似；
            3. 你不能决定优先级，不能合并已有卡片，不能修改或删除任何反馈原文；
            4. 你产出的所有内容都是草稿，必须由产品经理确认；
            5. 命中多种现象的反馈可以同时关联到多张卡片。
            现在请调用工具完成整理。""";

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AiSchemaValidator schemaValidator;
    private final AiProperties aiProperties;
    private final FeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;

    public AiOrchestrator(LlmClient llmClient, ToolRegistry toolRegistry, AiSchemaValidator schemaValidator,
                          AiProperties aiProperties, FeedbackMapper feedbackMapper, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.schemaValidator = schemaValidator;
        this.aiProperties = aiProperties;
        this.feedbackMapper = feedbackMapper;
        this.objectMapper = objectMapper;
    }

    /** @return 本次 AI 运行的操作人展示名（写入时间线），如 "AI(mock)" */
    public String aiName() {
        return "AI(" + ("openai".equals(aiProperties.provider()) ? aiProperties.model() : "mock") + ")";
    }

    /**
     * 对一批未处理反馈执行整理：聚类生成候选问题卡片。
     *
     * @param feedbackIds 参与整理的反馈 id；为空时取全部未处理反馈
     * @return 成功创建的候选卡片数
     * @throws AiUnavailableException 模型不可用（本轮无任何业务副作用）
     */
    public int extractForFeedbacks(List<Long> feedbackIds) {
        LambdaQueryWrapper<Feedback> qw = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getStatus, "UNPROCESSED")
                .in(feedbackIds != null && !feedbackIds.isEmpty(), Feedback::getId, feedbackIds)
                .orderByAsc(Feedback::getId);
        List<Feedback> feedbacks = feedbackMapper.selectList(qw);
        if (feedbacks.isEmpty()) {
            return 0;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mode", "extract");
        ArrayNode arr = payload.putArray("feedbacks");
        for (Feedback fb : feedbacks) {
            ObjectNode item = arr.addObject();
            item.put("id", fb.getId());
            item.put("content", fb.getContent());
        }
        ToolContext ctx = new ToolContext(aiName(), UUID.randomUUID().toString().substring(0, 8));
        return runToolLoop(payload.toString(), ctx);
    }

    /**
     * 为一个已确认的候选问题生成需求草稿。
     *
     * @param issueId 候选问题 id（必须处于 CONFIRMED 状态，由工具实现校验）
     * @return 需求草稿 id；模型未产出草稿时为 null
     * @throws AiUnavailableException 模型不可用
     */
    public Long draftForIssue(Long issueId, String issueTitle, String issueModule,
                              String issueProblem, String issueDemand, int feedbackCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mode", "draft");
        ObjectNode issue = payload.putObject("issue");
        issue.put("id", issueId);
        issue.put("title", issueTitle);
        issue.put("module", issueModule);
        issue.put("problem", issueProblem);
        issue.put("demand", issueDemand);
        payload.put("feedbackCount", feedbackCount);
        ToolContext ctx = new ToolContext(aiName(), UUID.randomUUID().toString().substring(0, 8));
        runToolLoop(payload.toString(), ctx);
        // 草稿 id 由 draft_requirement 工具登记进 ctx.results
        return ctx.results().stream()
                .map(ToolResult::data)
                .filter(Long.class::isInstance)
                .map(Long.class::cast)
                .findFirst().orElse(null);
    }

    /**
     * 工具循环主体：调用模型 → 执行其工具调用 → 回填结果 → 直到模型不再发起调用。
     *
     * @return 执行成功的工具调用次数
     */
    private int runToolLoop(String userPayload, ToolContext ctx) {
        List<LlmClient.ChatMessage> messages = new ArrayList<>();
        messages.add(LlmClient.ChatMessage.of("user", userPayload));
        List<LlmClient.ToolSpec> tools = toolRegistry.specs().stream()
                .map(spec -> new LlmClient.ToolSpec(spec.name(), spec.description(), spec.parameters()))
                .toList();
        int executed = 0;
        for (int round = 0; round < aiProperties.maxToolCalls(); round++) {
            List<LlmClient.LlmToolCall> calls = llmClient.chat(
                    new LlmClient.ChatRequest(SYSTEM_PROMPT, List.copyOf(messages), tools));
            if (calls.isEmpty()) {
                break;
            }
            messages.add(new LlmClient.ChatMessage("assistant", null, calls, null));
            for (LlmClient.LlmToolCall call : calls) {
                String resultMessage;
                try {
                    resultMessage = executeCall(call, ctx);
                } catch (Exception e) {
                    resultMessage = "工具执行异常: " + e.getMessage();
                }
                executed++;
                messages.add(LlmClient.ChatMessage.tool(call.id(), resultMessage));
            }
        }
        log.info("[ai-orchestrator] 运行结束，执行受限工具调用 {} 次", executed);
        return executed;
    }

    /** 执行单个工具调用：白名单 → Schema 校验 → 工具实现内业务约束。 */
    private String executeCall(LlmClient.LlmToolCall call, ToolContext ctx) {
        var tool = toolRegistry.get(call.name());
        List<String> schemaErrors = schemaValidator.validate(call.arguments(), tool.jsonSchema());
        if (!schemaErrors.isEmpty()) {
            return "参数校验未通过: " + String.join("; ", schemaErrors);
        }
        ToolResult result = tool.execute(call.arguments(), ctx);
        ctx.record(result);
        return result.success() ? result.message() : "业务约束拒绝: " + result.message();
    }
}

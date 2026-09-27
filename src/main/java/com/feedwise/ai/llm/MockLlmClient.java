package com.feedwise.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 离线确定性 AI 引擎（默认 provider=mock）。
 *
 * <p>与真实模型走<b>完全相同</b>的工具调用协议：同样只返回工具调用，由服务端工具层校验执行。
 * 聚类规则刻意保守，对应验收用例：</p>
 * <ul>
 *   <li>同一功能模块 + 同一"现象指纹"的多条不同说法 → 一张候选卡片；</li>
 *   <li>一条反馈命中多个现象指纹（如"上传失败"+"看不到退回原因"）→ 拆入两张卡片；</li>
 *   <li>关键词相似但现象指纹不同（"找不到退回原因" vs "找不到填写入口"）→ 永不合并；</li>
 *   <li>没有任何指纹命中的反馈 → 保持未处理，留给产品经理人工分类。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "feedwise.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    private final ObjectMapper objectMapper;

    public MockLlmClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 现象指纹规则：一个指纹对应一张候选卡片，是"相似反馈合并"的最小判定单位。 */
    private record Signature(String key, String title, String problem, String demand, Pattern pattern) {
    }

    private static final List<Signature> SIGNATURES = List.of(
            new Signature("invoice_upload_fail", "发票上传失败",
                    "上传发票时反复失败（转圈/报错/无响应），多个浏览器与端均复现",
                    "修复发票上传链路并给出明确的失败原因提示",
                    Pattern.compile("(发票|票据).{0,16}(上传|传).{0,24}(失败|报错|不行|没反应|转圈|卡)|上传.{0,6}发票.{0,12}(失败|报错)")),
            new Signature("return_reason_hidden", "报销单退回原因不可见",
                    "报销单被退回后，用户在系统里找不到退回原因",
                    "在退回通知与单据详情中展示退回原因",
                    Pattern.compile("(退回|打回).{0,16}(原因|哪里)|找不到.{0,8}退回(原因)?")),
            new Signature("approval_flow_heavy", "审批流程繁琐",
                    "审批节点多、流程繁琐，用户希望简化",
                    "支持 configurable 审批流或合并审批节点",
                    Pattern.compile("审批.{0,12}(麻烦|复杂|繁琐|人多|太多|慢|久)")),
            new Signature("approval_progress_hidden", "审批进度不透明",
                    "审批中看不到当前节点与进度，只能干等",
                    "展示审批进度与当前处理节点",
                    Pattern.compile("审批.{0,10}(进度|卡在|节点)|进度.{0,8}(看不到|查不到)")),
            new Signature("form_entry_hidden", "报销单填写入口难找",
                    "改版后用户找不到报销单填写入口",
                    "在首页提供显式的发起报销入口",
                    Pattern.compile("报销单.{0,10}填写.{0,6}(入口|菜单|哪里)|找不到.{0,8}(报销单|入口)")),
            new Signature("form_fill_hard", "报销单填写体验差",
                    "填写报销单时字段交互不佳（如金额不可粘贴、保存失败）",
                    "优化填写字段的交互与容错",
                    Pattern.compile("报销单.{0,12}(填写|粘贴|金额|字段|保存)"))
    );

    @Override
    public List<LlmToolCall> chat(ChatRequest request) {
        // 工具结果回填后无新指令 → 本轮结束
        boolean hasToolResult = request.messages().stream().anyMatch(m -> "tool".equals(m.role()));
        if (hasToolResult) {
            return List.of();
        }
        String payload = request.messages().get(request.messages().size() - 1).content();
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new AiUnavailableException("mock 引擎无法解析输入 payload", e);
        }
        String mode = root.path("mode").asText();
        if ("draft".equals(mode)) {
            return draftCalls(root);
        }
        return extractCalls(root);
    }

    /** 整理模式：按现象指纹聚类，输出 create_candidate_issue 工具调用。 */
    private List<LlmToolCall> extractCalls(JsonNode root) {
        JsonNode feedbacks = root.path("feedbacks");
        // 指纹 -> 关联反馈 id 列表；一条反馈可命中多个指纹（即拆分）
        Map<String, List<Long>> groups = new LinkedHashMap<>();
        Map<Long, String> contentById = new LinkedHashMap<>();
        for (JsonNode fb : feedbacks) {
            long id = fb.path("id").asLong();
            String content = fb.path("content").asText();
            contentById.put(id, content);
            for (Signature sig : SIGNATURES) {
                if (sig.pattern().matcher(content).find()) {
                    groups.computeIfAbsent(sig.key(), k -> new ArrayList<>()).add(id);
                }
            }
        }
        List<LlmToolCall> calls = new ArrayList<>();
        List<Signature> used = new ArrayList<>();
        for (Signature sig : SIGNATURES) {
            List<Long> ids = groups.get(sig.key());
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            used.add(sig);
            ObjectNode args = objectMapper.createObjectNode();
            args.put("title", sig.title() + "（" + ids.size() + " 条反馈）");
            args.put("module", moduleOf(sig.key()));
            args.put("problem", sig.problem());
            args.put("demand", sig.demand());
            ArrayNode idArr = args.putArray("feedbackIds");
            ids.forEach(idArr::add);
            // 同一条反馈命中多个指纹 → 已拆分，提示 PM 复核是否合并（AI 自身不做合并决策）
            List<Long> multiHit = ids.stream().filter(id -> countHits(contentById.get(id)) > 1).toList();
            if (!multiHit.isEmpty()) {
                args.put("similarNote", "含同时命中多类现象的反馈（id:" + multiHit + "），已按现象拆分为独立卡片，请复核是否合并");
            }
            calls.add(new LlmToolCall("mock_" + sig.key(), "create_candidate_issue", args));
        }
        log.info("[mock-ai] 聚类完成：{} 条反馈 → {} 张候选卡片", feedbacks.size(), calls.size());
        return calls;
    }

    /** 统计一条反馈命中的指纹数量（>1 即混合反馈）。 */
    private long countHits(String content) {
        return SIGNATURES.stream().filter(s -> s.pattern().matcher(content).find()).count();
    }

    private String moduleOf(String signatureKey) {
        return switch (signatureKey) {
            case "invoice_upload_fail" -> "INVOICE_UPLOAD";
            case "return_reason_hidden" -> "RETURN_MODIFY";
            case "approval_flow_heavy", "approval_progress_hidden" -> "APPROVAL";
            case "form_entry_hidden", "form_fill_hard" -> "REIMBURSE_FORM";
            default -> "OTHER";
        };
    }

    /** 起草模式：为已确认问题输出 draft_requirement 工具调用（确定性模板）。 */
    private List<LlmToolCall> draftCalls(JsonNode root) {
        JsonNode issue = root.path("issue");
        int feedbackCount = root.path("feedbackCount").asInt(0);
        ObjectNode args = objectMapper.createObjectNode();
        args.put("issueId", issue.path("id").asLong());
        args.put("title", "改进：" + issue.path("title").asText());
        args.put("background", "近一周收到多条与「" + issue.path("module").asText()
                + "」模块相关的用户反馈，问题现象： " + issue.path("problem").asText()
                + "。涉及原始反馈 " + feedbackCount + " 条，可在候选问题详情回查原文。");
        args.put("description", "针对「" + issue.path("demand").asText()
                + "」进行功能改进：明确问题根因，优化对应流程与提示，保证改动后用户可在界面内自助完成相关操作。");
        ArrayNode acceptance = args.putArray("acceptance");
        acceptance.add("问题现象可复现路径上不再出现该障碍");
        acceptance.add("用户能在界面内看到明确的处理结果或原因说明");
        acceptance.add("客服录入的同类反馈在上线后一周内明显下降");
        return List.of(new LlmToolCall("mock_draft_" + issue.path("id").asLong(), "draft_requirement", args));
    }
}

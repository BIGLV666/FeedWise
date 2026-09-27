package com.feedwise.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.feedwise.service.CandidateIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 受限工具：创建候选问题卡片。
 *
 * <p>业务红线（实现内强制，模型侧仅是提示）：</p>
 * <ul>
 *   <li>feedbackIds 中的反馈必须存在且未处理；同一反馈不得重复归入；</li>
 *   <li>产物状态固定 PENDING_REVIEW、aiGenerated=1，必须产品经理确认；</li>
 *   <li>本工具只能写候选卡片与关联关系，绝不修改反馈原文（原文不可变）。</li>
 * </ul>
 */
@Component
public class CreateCandidateIssueTool implements AiTool {

    private static final Logger log = LoggerFactory.getLogger(CreateCandidateIssueTool.class);

    private final CandidateIssueService candidateIssueService;

    public CreateCandidateIssueTool(CandidateIssueService candidateIssueService) {
        this.candidateIssueService = candidateIssueService;
    }

    @Override
    public String name() {
        return "create_candidate_issue";
    }

    @Override
    public String description() {
        return "把一组表达了同一个问题的脱敏用户反馈聚合成一张候选问题卡片。"
                + "要求：这组反馈必须是同一个功能模块下的同一现象；含义不同（现象不同）的反馈不得放入同一张卡片，"
                + "应拆成多张卡片分别调用本工具。产物为待产品经理确认的草稿。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string", "minLength", 1, "maxLength", 60, "description", "问题标题"),
                        "module", Map.of("type", "string", "enum", List.of("REIMBURSE_FORM", "INVOICE_UPLOAD", "APPROVAL", "RETURN_MODIFY", "OTHER")),
                        "problem", Map.of("type", "string", "minLength", 1, "maxLength", 200, "description", "问题现象归纳"),
                        "demand", Map.of("type", "string", "maxLength", 200, "description", "用户诉求归纳"),
                        "similarNote", Map.of("type", "string", "maxLength", 200, "description", "给产品经理的疑似相似提示（可选）"),
                        "feedbackIds", Map.of("type", "array", "items", Map.of("type", "integer"), "minItems", 1, "maxItems", 50)),
                "required", List.of("title", "module", "problem", "feedbackIds"));
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext ctx) {
        List<Long> feedbackIds = new java.util.ArrayList<>();
        for (JsonNode id : args.path("feedbackIds")) {
            feedbackIds.add(id.asLong());
        }
        try {
            Long issueId = candidateIssueService.createCard(
                    args.path("title").asText(),
                    args.path("module").asText("OTHER"),
                    args.path("problem").asText(),
                    args.path("demand").asText(""),
                    args.hasNonNull("similarNote") ? args.path("similarNote").asText() : null,
                    feedbackIds,
                    true,
                    null,
                    ctx.aiName());
            return ToolResult.ok("候选问题卡片已创建（待产品经理确认），id=" + issueId, issueId);
        } catch (Exception e) {
            // 业务约束拒绝：不抛出，作为结果返回给模型/日志，本轮其他工具调用不受影响
            log.warn("[ai-tool] create_candidate_issue 被业务约束拒绝: {}", e.getMessage());
            return ToolResult.reject(e.getMessage());
        }
    }
}

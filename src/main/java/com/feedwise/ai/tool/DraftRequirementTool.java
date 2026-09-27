package com.feedwise.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.feedwise.service.DraftCoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 受限工具：为已确认的候选问题生成需求说明与验收条件<b>草稿</b>。
 *
 * <p>业务红线：仅限 CONFIRMED 状态的问题；一个问题同时只允许一张活动草稿；
 * 产物 aiGenerated=1、状态 DRAFT，必须产品经理修改确认后才能建立改进任务。</p>
 */
@Component
public class DraftRequirementTool implements AiTool {

    private static final Logger log = LoggerFactory.getLogger(DraftRequirementTool.class);

    private final DraftCoreService draftCoreService;

    public DraftRequirementTool(DraftCoreService draftCoreService) {
        this.draftCoreService = draftCoreService;
    }

    @Override
    public String name() {
        return "draft_requirement";
    }

    @Override
    public String description() {
        return "为一个已由产品经理确认的候选问题起草需求说明与验收条件。产物只是草稿，必须产品经理确认后才会变成改进任务。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issueId", Map.of("type", "integer", "description", "已确认的候选问题 id"),
                        "title", Map.of("type", "string", "minLength", 1, "maxLength", 60),
                        "background", Map.of("type", "string", "minLength", 1, "maxLength", 500, "description", "背景说明"),
                        "description", Map.of("type", "string", "minLength", 1, "maxLength", 1000, "description", "需求说明"),
                        "acceptance", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1, "maxItems", 10, "description", "验收条件列表")),
                "required", List.of("issueId", "title", "background", "description", "acceptance"));
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext ctx) {
        List<String> acceptance = new ArrayList<>();
        for (JsonNode item : args.path("acceptance")) {
            acceptance.add(item.asText());
        }
        try {
            Long draftId = draftCoreService.createCore(
                    args.path("issueId").asLong(),
                    args.path("title").asText(),
                    args.path("background").asText(),
                    args.path("description").asText(),
                    acceptance,
                    true,
                    null,
                    ctx.aiName());
            return ToolResult.ok("需求草稿已生成（待产品经理确认），id=" + draftId, draftId);
        } catch (Exception e) {
            log.warn("[ai-tool] draft_requirement 被业务约束拒绝: {}", e.getMessage());
            return ToolResult.reject(e.getMessage());
        }
    }
}

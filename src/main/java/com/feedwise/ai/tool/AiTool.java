package com.feedwise.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * AI 受限工具 SPI。AI 只能调用注册进 {@link ToolRegistry} 的工具，
 * 工具实现内强制业务红线（如"含义不同的反馈不得合并""原始反馈不可修改"）。
 * 工具产物一律是"待确认草稿"，没有任何工具能直接生效到业务终态。
 */
public interface AiTool {

    /** 工具名（模型可见）。 */
    String name();

    /** 工具用途描述（模型可见）。 */
    String description();

    /** JSON Schema（OpenAI function parameters 形态），同时用于入参校验。 */
    Map<String, Object> jsonSchema();

    /**
     * 执行工具。
     *
     * @param args 模型给出的入参（已过 Schema 校验，业务约束仍需在此二次校验）
     * @param ctx  运行上下文
     * @return 执行结果；业务约束拒绝时返回 success=false，而不是抛异常
     */
    ToolResult execute(JsonNode args, ToolContext ctx);
}

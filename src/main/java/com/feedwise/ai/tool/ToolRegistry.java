package com.feedwise.ai.tool;

import io.github.biglv666.webcommon.exception.BusinessException;
import com.feedwise.common.error.FeedWiseErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受限工具白名单注册表。不在注册表里的工具名一律拒绝——
 * 这是"AI 不能自由发挥"的第一道闸门；第二道闸门是工具实现内的业务约束校验。
 */
@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AiTool> allTools) {
        for (AiTool tool : allTools) {
            tools.put(tool.name(), tool);
        }
    }

    /**
     * 按名取工具。
     *
     * @param name 工具名
     * @return 工具实例
     * @throws BusinessException 工具不在白名单内（AI 越权调用）
     */
    public AiTool get(String name) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            throw new BusinessException(FeedWiseErrorCode.AI_TOOL_REJECTED, "未注册的 AI 工具: " + name);
        }
        return tool;
    }

    /** @return 全部工具的 LLM 规格描述（function calling 白名单）。 */
    public List<LlmToolSpec> specs() {
        List<LlmToolSpec> specs = new ArrayList<>();
        for (AiTool tool : tools.values()) {
            specs.add(new LlmToolSpec(tool.name(), tool.description(), tool.jsonSchema()));
        }
        return specs;
    }

    /** 与 {@link LlmClient.ToolSpec} 解耦的规格载体。 */
    public record LlmToolSpec(String name, String description, Map<String, Object> parameters) {
    }
}

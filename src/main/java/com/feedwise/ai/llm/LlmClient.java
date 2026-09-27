package com.feedwise.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 大模型客户端抽象。FeedWise 的关键约束：模型不直接产出业务数据，
 * 只能返回"调用哪个受限工具、用什么参数"，由服务端工具层校验后执行。
 */
public interface LlmClient {

    /**
     * 发起一轮对话，返回模型请求的工具调用列表（可能为空，表示整理结束）。
     *
     * @param request 系统提示、消息序列与可用工具白名单
     * @return 模型发起的工具调用；空列表表示本轮无更多动作
     * @throws AiUnavailableException 模型不可用 / 超时 / 响应不合法
     */
    List<LlmToolCall> chat(ChatRequest request);

    /** 对话请求：system 提示 + 完整消息序列（含工具结果回填）+ 工具白名单。 */
    record ChatRequest(String systemPrompt, List<ChatMessage> messages, List<ToolSpec> tools) {
    }

    /**
     * 对话消息。
     *
     * @param role       system / user / assistant / tool
     * @param content    文本内容（assistant 的工具调用序列化到 toolCalls）
     * @param toolCalls  assistant 发起的工具调用
     * @param toolCallId tool 角色消息对应的调用 id
     */
    record ChatMessage(String role, String content, List<LlmToolCall> toolCalls, String toolCallId) {

        public static ChatMessage of(String role, String content) {
            return new ChatMessage(role, content, null, null);
        }

        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    /** 工具白名单条目（OpenAI function 形态的 JSON Schema）。 */
    record ToolSpec(String name, String description, Map<String, Object> parameters) {
    }

    /** 模型发起的一次工具调用。 */
    record LlmToolCall(String id, String name, JsonNode arguments) {
    }
}

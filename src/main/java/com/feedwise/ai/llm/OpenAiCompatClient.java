package com.feedwise.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 function-calling 客户端（provider=openai 时启用，支持智谱 GLM 等）。
 *
 * <p>边界控制：单次 HTTP 调用超时可配；仅解析 tool_calls；任何网络/协议异常
 * 统一抛 {@link AiUnavailableException}，由上层降级为手工分类，绝不半写业务数据。</p>
 */
@Component
@ConditionalOnProperty(name = "feedwise.ai.provider", havingValue = "openai")
public class OpenAiCompatClient implements LlmClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .build();
    }

    @Override
    public List<LlmToolCall> chat(ChatRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiUnavailableException("未配置 AI_API_KEY，无法调用模型");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.model());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        for (ChatMessage msg : request.messages()) {
            if ("tool".equals(msg.role())) {
                messages.add(Map.of("role", "tool", "tool_call_id", msg.toolCallId(), "content", msg.content()));
            } else if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (LlmToolCall call : msg.toolCalls()) {
                    calls.add(Map.of("id", call.id(), "type", "function",
                            "function", Map.of("name", call.name(), "arguments", call.arguments().toString())));
                }
                Map<String, Object> m = new HashMap<>();
                m.put("role", "assistant");
                m.put("content", msg.content());
                m.put("tool_calls", calls);
                messages.add(m);
            } else {
                messages.add(Map.of("role", msg.role(), "content", msg.content()));
            }
        }
        body.put("messages", messages);
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolSpec spec : request.tools()) {
            tools.add(Map.of("type", "function",
                    "function", Map.of("name", spec.name(), "description", spec.description(), "parameters", spec.parameters())));
        }
        body.put("tools", tools);
        body.put("tool_choice", "auto");

        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiUnavailableException("请求序列化失败", e);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(properties.timeoutMs()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new AiUnavailableException("模型调用失败：" + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new AiUnavailableException("模型返回 HTTP " + response.statusCode());
        }
        return parseToolCalls(response.body());
    }

    /** 从响应体解析 tool_calls；无工具调用时返回空列表。 */
    private List<LlmToolCall> parseToolCalls(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            List<LlmToolCall> result = new ArrayList<>();
            for (JsonNode call : toolCalls) {
                String argsJson = call.path("function").path("arguments").asText("{}");
                result.add(new LlmToolCall(
                        call.path("id").asText("call_0"),
                        call.path("function").path("name").asText(),
                        objectMapper.readTree(argsJson)));
            }
            return result;
        } catch (Exception e) {
            throw new AiUnavailableException("模型响应解析失败", e);
        }
    }
}

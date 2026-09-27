package com.feedwise.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 配置项。
 *
 * @param provider    mock=离线确定性引擎（默认，演示/测试零依赖）；openai=OpenAI 兼容 function calling
 * @param baseUrl     OpenAI 兼容服务地址（如智谱 GLM 的 /api/paas/v4）
 * @param apiKey      API Key，为空时 openai 模式启动即失败
 * @param model       模型名
 * @param timeoutMs   单次调用超时（毫秒）
 * @param maxToolCalls 单轮整理最多执行的受限工具调用次数（防失控）
 */
@ConfigurationProperties(prefix = "feedwise.ai")
public record AiProperties(String provider, String baseUrl, String apiKey, String model,
                           long timeoutMs, int maxToolCalls) {

    public AiProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 20000;
        }
        if (maxToolCalls <= 0) {
            maxToolCalls = 20;
        }
    }
}

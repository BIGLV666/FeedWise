package com.feedwise.ai.llm;

/** AI 模型不可用（超时/网络/响应不合法）。触发降级：反馈保持未处理，产品经理可手工分类。 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

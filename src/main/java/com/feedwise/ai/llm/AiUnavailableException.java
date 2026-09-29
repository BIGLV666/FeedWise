package com.feedwise.ai.llm;

import com.feedwise.common.error.FeedWiseErrorCode;
import io.github.biglv666.webcommon.annotation.DefaultErrorCode;
import org.outboxpro.core.annotation.NonRetryable;

/**
 * AI 模型不可用（超时/网络/响应不合法）。
 * 同步触发路径：@DefaultErrorCode 映射 50301，提示改用手工分类；
 * 事件消费路径：@NonRetryable 标注——模型不可用不是瞬时故障，跳过退避重试直接进死信，
 * 等模型恢复后由产品经理在 DLQ 页手工重放。
 */
@NonRetryable
@DefaultErrorCode(value = FeedWiseErrorCode.class, constant = "AI_UNAVAILABLE")
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

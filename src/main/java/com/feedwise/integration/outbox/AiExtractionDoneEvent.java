package com.feedwise.integration.outbox;

import com.feedwise.config.OutboxConfig;
import org.outboxpro.core.annotation.OutboxEvent;

/**
 * AI 整理完成通知（注解式事件声明，BEST_EFFORT 订阅）。
 * 与编程式 feedback.batch.imported 两种声明风格并存展示。
 *
 * @param batchId   批次号
 * @param cards     生成候选卡片数
 * @param feedbacks 参与反馈数
 * @param operatorName 操作人展示名
 */
@OutboxEvent(eventType = "ai.extraction.done", exchange = OutboxConfig.FEEDBACK_EXCHANGE)
public record AiExtractionDoneEvent(String batchId, int cards, int feedbacks, String operatorName) {
}

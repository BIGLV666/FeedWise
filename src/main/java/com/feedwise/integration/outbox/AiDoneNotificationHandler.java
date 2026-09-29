package com.feedwise.integration.outbox;

import com.feedwise.service.OperationLogService;
import org.outboxpro.core.annotation.OutboxHandler;
import org.outboxpro.core.context.EventContext;
import org.outboxpro.core.handler.AnnotatedOutboxHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 整理完成通知消费者（注解式 + BEST_EFFORT 模式）。
 * 写"工作台动态"；BEST_EFFORT：失败只记 IGNORED 并 ACK，不阻塞队列——
 * 动态通知是可丢失的非关键链路（与导入事件的 RELIABLE 对照）。
 */
@Component
@ConditionalOnProperty(name = "outboxpro.enabled", havingValue = "true", matchIfMissing = true)
@OutboxHandler(event = AiExtractionDoneEvent.class, queue = "feedwise.notify.queue",
        consumerName = "feedwise-notify")
public class AiDoneNotificationHandler extends AnnotatedOutboxHandler<AiExtractionDoneEvent> {

    private static final Logger log = LoggerFactory.getLogger(AiDoneNotificationHandler.class);

    private final OperationLogService operationLogService;

    public AiDoneNotificationHandler(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Override
    public void handle(EventContext<AiExtractionDoneEvent> context) {
        AiExtractionDoneEvent event = context.getPayload();
        operationLogService.log("AI_RUN", null, "AI_DONE_NOTIFIED", null, "outbox-notify",
                "AI 整理完成（批次 " + event.batchId() + "）：" + event.feedbacks() + " 条反馈 → "
                        + event.cards() + " 张候选卡片，操作方：" + event.operatorName());
        log.info("[notify] 批次 {} 动态已落时间线", event.batchId());
    }
}

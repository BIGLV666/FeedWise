package com.feedwise.integration.outbox;

import com.feedwise.ai.orchestrator.AiOrchestrator;
import com.feedwise.config.OutboxConfig;
import com.feedwise.ai.llm.AiUnavailableException;
import com.feedwise.service.OperationLogService;
import org.outboxpro.core.context.EventContext;
import org.outboxpro.core.handler.OutboxProHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 整理消费者：订阅 feedback.batch.imported，调用受约束编排器生成候选问题卡片。
 *
 * <p>可靠性：RELIABLE 模式 + Inbox 幂等（consumerName+eventId）；模型不可用时记录失败时间线
 * 并重新抛出，由 OutboxPro 退避重试，耗尽后进入 DLQ 等待人工处理——反馈数据零丢失。</p>
 */
@Component
public class AiExtractionHandler implements OutboxProHandler<FeedbackBatchImportedEvent> {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionHandler.class);

    private final AiOrchestrator aiOrchestrator;
    private final OperationLogService operationLogService;

    public AiExtractionHandler(AiOrchestrator aiOrchestrator, OperationLogService operationLogService) {
        this.aiOrchestrator = aiOrchestrator;
        this.operationLogService = operationLogService;
    }

    @Override
    public String eventType() {
        return OutboxConfig.FEEDBACK_BATCH_IMPORTED;
    }

    @Override
    public Class<FeedbackBatchImportedEvent> payloadType() {
        return FeedbackBatchImportedEvent.class;
    }

    @Override
    public String consumerName() {
        return "feedwise-ai";
    }

    @Override
    public void handle(EventContext<FeedbackBatchImportedEvent> context) {
        FeedbackBatchImportedEvent event = context.getPayload();
        try {
            int cards = aiOrchestrator.extractForFeedbacks(event.feedbackIds());
            operationLogService.log("AI_RUN", null, "AI_EXTRACT_DONE", null, aiOrchestrator.aiName(),
                    "批次 " + context.getEventId() + "：反馈 " + event.feedbackIds() + " → 生成候选卡片 " + cards + " 张");
            log.info("[ai-handler] 批次 {} 完成，生成 {} 张候选卡片", context.getEventId(), cards);
        } catch (AiUnavailableException e) {
            operationLogService.log("AI_RUN", null, "AI_RUN_FAILED", null, aiOrchestrator.aiName(),
                    "批次 " + context.getEventId() + " 模型不可用：" + e.getMessage() + "；反馈保持未处理，可手工分类");
            throw e;
        }
    }
}

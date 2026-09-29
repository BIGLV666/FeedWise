package com.feedwise.config;

import com.feedwise.integration.outbox.FeedbackBatchImportedEvent;
import io.github.biglv666.authkit.AuthKit;
import org.outboxpro.core.event.EventDefinition;
import org.outboxpro.core.subscription.EventBinding;
import org.outboxpro.core.subscription.OutboxProSubscription;
import org.outboxpro.spi.deadletter.DlqReplayAuthorizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OutboxPro 事件与订阅声明。
 * 链路：客服导入反馈（事务内写 outbox）→ Relay → RabbitMQ → AI 整理消费者（RELIABLE，可重试/死信）。
 */
@Configuration
public class OutboxConfig {

    public static final String FEEDBACK_BATCH_IMPORTED = "feedback.batch.imported";
    public static final String FEEDBACK_EXCHANGE = "feedwise.exchange";

    /** 事件定义：类型、载荷与 RabbitMQ 路由。 */
    @Bean
    public EventDefinition<FeedbackBatchImportedEvent> feedbackBatchImportedDefinition() {
        return EventDefinition.<FeedbackBatchImportedEvent>builder()
                .eventType(FEEDBACK_BATCH_IMPORTED)
                .schemaVersion("v1")
                .payloadType(FeedbackBatchImportedEvent.class)
                .route(FEEDBACK_EXCHANGE, FEEDBACK_BATCH_IMPORTED)
                .build();
    }

    /**
     * DLQ 重放/运维检索授权器：仅 PM 允许（scope 判权演示）。
     * 未登录调用（如 actuator 直连）一律拒绝。
     */
    @Bean
    public DlqReplayAuthorizer dlqReplayAuthorizer() {
        return (scope, operator) -> {
            if (!AuthKit.isLogin() || !AuthKit.hasRole("PM")) {
                throw new SecurityException("死信重放与运维检索仅产品经理可操作（scope=" + scope + "）");
            }
        };
    }

    /** AI 整理订阅：RELIABLE 模式，消费失败自动重试并最终进入 DLQ，保证反馈不被漏处理。 */
    @Bean
    public OutboxProSubscription aiExtractionSubscription() {
        return OutboxProSubscription.builder()
                .name("feedwise-ai")
                .exchange(FEEDBACK_EXCHANGE)
                .queue("feedwise.ai.extraction.queue")
                .bindings(EventBinding.reliable(FEEDBACK_BATCH_IMPORTED, FEEDBACK_BATCH_IMPORTED, FeedbackBatchImportedEvent.class))
                .build();
    }
}

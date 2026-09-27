package com.feedwise.integration.outbox;

import com.feedwise.config.OutboxConfig;
import com.feedwise.service.FeedbackService;
import org.outboxpro.core.OutboxProPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Outbox 发布桥接：把可选的 {@link OutboxProPublisher} 适配成业务端口。
 * OutboxPro 被禁用（如单测环境）时 publish 记日志即可，业务录入不被阻断。
 */
@Component
public class OutboxBridge implements FeedbackService.OutboxPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxBridge.class);

    private final ObjectProvider<OutboxProPublisher> publisherProvider;

    public OutboxBridge(ObjectProvider<OutboxProPublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    @Override
    public void publish(FeedbackBatchImportedEvent event) {
        OutboxProPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            log.warn("[outbox] Publisher 不存在（outboxpro.enabled=false），事件未投递: {}", event);
            return;
        }
        publisher.publish(OutboxConfig.FEEDBACK_BATCH_IMPORTED, event);
    }
}

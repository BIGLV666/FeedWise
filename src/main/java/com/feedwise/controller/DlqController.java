package com.feedwise.controller;

import com.feedwise.common.CurrentUser;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.service.OperationLogService;
import io.github.biglv666.authkit.annotation.RequirePermission;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.outboxpro.spi.deadletter.DeadLetterQuery;
import org.outboxpro.spi.deadletter.DeadLetterRecord;
import org.outboxpro.spi.deadletter.DeadLetterRepository;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 死信管理（PM 专属）：台账查询 + 人工重放。
 *
 * <p>复用组件的 DeadLetterRepository 台账与 RabbitTemplate 发布路径（与组件
 * actuator 端点同一套流程：claim 租约 → 重发原交换机/路由键 → Confirm 成功才标记 REPLAYED），
 * 鉴权由业务层的 {@code @RequirePermission("dlq:replay")} 完成——组件的
 * {@code DlqReplayAuthorizer} 则约束 actuator 直连场景（未登录一律拒绝）。</p>
 */
@RestController
@RequestMapping("/api/dlq")
public class DlqController {

    private final ObjectProvider<DeadLetterRepository> deadLetterRepositoryProvider;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final OperationLogService operationLogService;
    private final int maxReplayCount;
    private final long confirmTimeoutMillis;

    public DlqController(ObjectProvider<DeadLetterRepository> deadLetterRepositoryProvider,
                         ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                         OperationLogService operationLogService,
                         @Value("${outboxpro.dlq.ledger.max-replay-count:3}") int maxReplayCount) {
        this.deadLetterRepositoryProvider = deadLetterRepositoryProvider;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.operationLogService = operationLogService;
        this.maxReplayCount = maxReplayCount;
        this.confirmTimeoutMillis = 10_000;
    }

    /** 台账 Bean 只在 OutboxPro 启用时存在（测试环境关闭 → 端点降级提示）。 */
    private DeadLetterRepository ledger() {
        DeadLetterRepository repo = deadLetterRepositoryProvider.getIfAvailable();
        if (repo == null) {
            throw new BusinessException(FeedWiseErrorCode.AI_UNAVAILABLE, "OutboxPro 未启用，死信台账不可用");
        }
        return repo;
    }

    /** 死信台账分页查询。 */
    @GetMapping
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        DeadLetterRepository deadLetterRepository = ledger();
        DeadLetterQuery query = new DeadLetterQuery(null, null, null, (page - 1) * size, size);
        List<DeadLetterRecord> records = deadLetterRepository.findDeadLetters(query);
        long total = deadLetterRepository.countDeadLetters(query);
        return Map.of("records", records, "total", total);
    }

    /** 重放请求体。 */
    public record ReplayRequest(String reason) {
    }

    /**
     * 人工重放（按事件 ID 批量重放该事件的所有死信记录）。
     *
     * @param eventId 事件 ID
     * @param request 重放原因（写入台账审计）
     * @return replayed 成功条数
     */
    @PostMapping("/{eventId}/replay")
    @RequirePermission("dlq:replay")
    public Map<String, Object> replay(@PathVariable String eventId, @RequestBody ReplayRequest request) {
        String operator = "用户#" + CurrentUser.id();
        String reason = request.reason() == null || request.reason().isBlank() ? "手工重放" : request.reason();
        DeadLetterRepository deadLetterRepository = ledger();
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getObject();
        deadLetterRepository.recoverExpiredReplays(Instant.now());
        List<DeadLetterRecord> records = deadLetterRepository.claimReplayByEventId(
                eventId, "feedwise-ui", maxReplayCount, operator, reason, Instant.now());
        if (records.isEmpty()) {
            throw new BusinessException(FeedWiseErrorCode.MERGE_TARGET_INVALID, "没有待重放的死信记录（或已达最大重放次数）");
        }
        int succeeded = 0;
        int failed = 0;
        for (DeadLetterRecord record : records) {
            try {
                CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
                rabbitTemplate.convertAndSend(record.originalExchange(), record.originalRoutingKey(),
                        record.payloadJson().getBytes(StandardCharsets.UTF_8), message -> {
                            message.getMessageProperties().setHeader("x-outboxpro-attempt", 1);
                            message.getMessageProperties().setHeader("x-outboxpro-replayed", true);
                            message.getMessageProperties().setHeader("x-outboxpro-replay-operator", operator);
                            message.getMessageProperties().setHeader("x-outboxpro-original-dead-letter-id", record.id());
                            return message;
                        }, correlation);
                if (correlation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS).isAck()) {
                    deadLetterRepository.markReplaySucceeded(record.id(), "feedwise-ui", Instant.now());
                    succeeded++;
                } else {
                    deadLetterRepository.releaseReplay(record.id(), "feedwise-ui",
                            "RabbitMQ rejected replay message", Instant.now());
                    failed++;
                }
            } catch (Exception e) {
                deadLetterRepository.releaseReplay(record.id(), "feedwise-ui", e.getMessage(), Instant.now());
                failed++;
            }
        }
        operationLogService.log("AI_RUN", null, "DLQ_REPLAYED", CurrentUser.id(), operator,
                "重放事件 " + eventId + "（原因：" + reason + "），成功 " + succeeded + " 条，失败 " + failed + " 条");
        return Map.of("replayed", succeeded, "failed", failed);
    }
}

package com.feedwise.integration.guard;

import com.feedwise.service.OperationLogService;
import io.github.biglv666.guard.event.GuardRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * guard 拒绝事件订阅：幂等拒绝/锁超时也写入操作时间线，
 * 使"被拦截的重复提交"在全局历史查询中可回查（审计完整性）。
 */
@Component
public class GuardEventListener {

    private static final Logger log = LoggerFactory.getLogger(GuardEventListener.class);

    private final OperationLogService operationLogService;

    public GuardEventListener(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @EventListener
    public void onGuardRejected(GuardRejectedEvent event) {
        log.info("[guard-event] 类型={} key={} 方法={}", event.getType(), event.getKey(), event.getMethod());
        operationLogService.log("AI_RUN", null, "GUARD_REJECTED", null, "concurrent-guard",
                "并发防护拦截：" + event.getType() + "（key=" + event.getKey() + "，方法=" + event.getMethod() + "）");
    }
}

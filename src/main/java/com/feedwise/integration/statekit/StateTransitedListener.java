package com.feedwise.integration.statekit;

import com.feedwise.service.OperationLogService;
import io.github.biglv666.statekit.event.StateTransitedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 状态流转成功后的旁路监听（@TransactionalEventListener AFTER_COMMIT：
 * 事务回滚不会触发，保证时间线与最终状态一致）。
 * 统一把 task 机器的流转写进操作时间线（操作人来自 state-kit 的 auth-kit operatorResolver）。
 */
@Component
public class StateTransitedListener {

    private static final Logger log = LoggerFactory.getLogger(StateTransitedListener.class);

    private final OperationLogService operationLogService;

    public StateTransitedListener(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransited(StateTransitedEvent event) {
        log.info("[state-event] machine={} entity={} {} → {}", event.getMachine(), event.getEntityId(),
                event.getFrom(), event.getTo());
        if (!"task".equals(event.getMachine())) {
            return;
        }
        Long operatorId = parseOperator(event.getOperatorId());
        operationLogService.log("TASK", parseLong(String.valueOf(event.getEntityId())), "TASK_STATE_CHANGED",
                operatorId, operatorId == null ? "system" : "用户#" + operatorId,
                event.getEvent() + "：" + event.getFrom() + " → " + event.getTo()
                        + (event.getTraceId() == null ? "" : "（traceId=" + event.getTraceId() + "）"));
    }

    private Long parseOperator(String operatorId) {
        try {
            return operatorId == null || operatorId.isBlank() ? null : Long.parseLong(operatorId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }
}

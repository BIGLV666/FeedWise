package com.feedwise.integration.statekit;

import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.enums.TaskStatus;
import io.github.biglv666.statekit.StateGuard;
import io.github.biglv666.statekit.StateTx;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * task 状态机守卫（state-kit 扩展点，yml 引用 bean 名 verifyResultGuard）：
 * PASS/REJECT 流转必须携带验证结果——FireArg.param("verifyResult")。
 * 守卫在 CAS 前执行，抛异常即拒绝流转且状态不变（可携带业务错误码）。
 */
@Component("verifyResultGuard")
public class VerifyResultGuard implements StateGuard<TaskStatus, Long> {

    @Override
    public boolean test(StateTx<TaskStatus, Long> tx) {
        String note = tx.param("verifyResult", String.class);
        if (note == null || note.isBlank()) {
            throw new BusinessException(FeedWiseErrorCode.VERIFY_RESULT_REQUIRED);
        }
        return true;
    }
}

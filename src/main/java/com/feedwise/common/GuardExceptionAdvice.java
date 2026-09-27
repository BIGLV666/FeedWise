package com.feedwise.common;

import com.feedwise.common.error.FeedWiseErrorCode;
import io.github.biglv666.guard.idempotent.IdempotentRejectedException;
import io.github.biglv666.guard.lock.LockAcquireTimeoutException;
import io.github.biglv666.webcommon.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * guard 并发防护异常 → 统一 Result 映射。
 * web-common 的兜底处理器只识别自身异常体系，这里把幂等拒绝/锁超时
 * 转成业务错误码 40906（前端展示为"冲突/重复提交"反馈，而不是 500）。
 */
@RestControllerAdvice
public class GuardExceptionAdvice {

    /** 重复提交被幂等组件拒绝 → 40906 冲突反馈。 */
    @ExceptionHandler(IdempotentRejectedException.class)
    public Result<Void> handleIdempotentRejected(IdempotentRejectedException e) {
        return Result.fail(FeedWiseErrorCode.DUPLICATE_REQUEST, e.getMessage());
    }

    /** 分布式锁获取超时（并发冲突）→ 同样按冲突反馈。 */
    @ExceptionHandler(LockAcquireTimeoutException.class)
    public Result<Void> handleLockTimeout(LockAcquireTimeoutException e) {
        return Result.fail(FeedWiseErrorCode.DUPLICATE_REQUEST, "操作正在进行中，请稍后重试");
    }
}

package com.feedwise.integration.governance;

import com.feedwise.service.OperationLogService;
import io.github.biglv666.apigovernance.async.annotation.AsyncHandler;
import io.github.biglv666.apigovernance.async.event.AsyncEvent;
import io.github.biglv666.apigovernance.async.event.AsyncPhase;
import org.springframework.stereotype.Component;

/**
 * api-governance 异步钩子：AI 整理动作的四阶段旁路观察。
 * 目标方法（/api/ai/extract）保持同步执行，Handler 异步旁路运行、不改变业务结果；
 * 耗时与失败摘要写入操作时间线，供演示"异步可观测"。
 */
@Component
public class AiExtractAsyncHandlers {

    private final OperationLogService operationLogService;

    public AiExtractAsyncHandlers(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /** AI 整理成功后异步记录耗时。 */
    @AsyncHandler(value = "feedwise.ai.extract", phase = AsyncPhase.AFTER_SUCCESS, order = 100)
    public void onSuccess(AsyncEvent event) {
        operationLogService.log("AI_RUN", null, "AI_ASYNC_OBSERVED", null, "api-governance",
                "异步钩子观察：AI 整理完成，耗时 " + event.elapsedMillis() + "ms（" + event.sourceMethod() + "）");
    }

    /** AI 整理失败（如模型不可用）异步记录错误摘要。 */
    @AsyncHandler(value = "feedwise.ai.extract", phase = AsyncPhase.AFTER_ERROR, order = 100)
    public void onError(AsyncEvent event) {
        String summary = event.error() == null ? "未知错误" : event.error().toString();
        operationLogService.log("AI_RUN", null, "AI_ASYNC_ERROR", null, "api-governance",
                "异步钩子观察：AI 整理失败，" + summary);
    }
}

package com.feedwise.integration.statekit;

import com.feedwise.entity.ImprovementTask;
import com.feedwise.enums.TaskStatus;
import com.feedwise.mapper.ImprovementTaskMapper;
import com.feedwise.service.OperationLogService;
import io.github.biglv666.statekit.StateAction;
import io.github.biglv666.statekit.StateTx;
import org.springframework.stereotype.Component;

/**
 * task 状态机动作（state-kit 扩展点，yml 引用 bean 名 taskPassAction）：
 * PASS 流转成功后与状态变更同事务执行——把验证结论联动写回来源候选问题的时间线，
 * 使"问题 → 任务 → 验证通过"的闭环在候选问题详情里也能一眼看到。
 */
@Component("taskPassAction")
public class TaskPassAction implements StateAction<TaskStatus, Long> {

    private final ImprovementTaskMapper taskMapper;
    private final OperationLogService operationLogService;

    public TaskPassAction(ImprovementTaskMapper taskMapper, OperationLogService operationLogService) {
        this.taskMapper = taskMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public void execute(StateTx<TaskStatus, Long> tx) {
        ImprovementTask task = taskMapper.selectById(tx.entityId());
        if (task == null) {
            return;
        }
        String verifyResult = tx.param("verifyResult", String.class);
        operationLogService.log("ISSUE", task.getIssueId(), "ISSUE_TASK_DONE", null, "状态机动作",
                "关联任务 #" + task.getId() + " 验证通过，验证结论：" + verifyResult);
    }
}

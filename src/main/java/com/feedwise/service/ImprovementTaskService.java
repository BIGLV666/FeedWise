package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.ImprovementTask;
import com.feedwise.entity.RequirementDraft;
import com.feedwise.enums.TaskStatus;
import com.feedwise.mapper.ImprovementTaskMapper;
import com.feedwise.mapper.UserMapper;
import io.github.biglv666.statekit.FireArg;
import io.github.biglv666.statekit.StateMachine;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 改进任务服务：由已确认草稿创建，开发/测试经状态机推进（TODO→IN_PROGRESS→PENDING_VERIFY→DONE，
 * 验证不通过 REJECT 退回开发中）。所有状态写入都经 state-kit task 状态机 CAS 完成，
 * TODO→DONE 等禁跳一律 40900 冲突。
 */
@Service
public class ImprovementTaskService {

    /** 开发/测试可执行的状态事件集合 */
    private static final Set<String> ALLOWED_EVENTS = Set.of("START", "SUBMIT", "PASS", "REJECT");

    private final ImprovementTaskMapper taskMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final StateMachine<TaskStatus, Long> taskMachine;

    public ImprovementTaskService(ImprovementTaskMapper taskMapper, UserMapper userMapper,
                                  OperationLogService operationLogService,
                                  @Qualifier("task") StateMachine<TaskStatus, Long> taskMachine) {
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.operationLogService = operationLogService;
        this.taskMachine = taskMachine;
    }

    /**
     * 由已确认草稿创建 TODO 任务（仅由 {@link RequirementDraftService#convertToTask} 调用）。
     *
     * @return 新任务 id
     */
    @Transactional
    public Long createFromDraft(RequirementDraft draft, Long assigneeId, String priority, String detail,
                                Long operatorId, String operatorName) {
        ImprovementTask task = new ImprovementTask();
        task.setDraftId(draft.getId());
        task.setIssueId(draft.getIssueId());
        task.setTitle(draft.getTitle());
        task.setDetail(detail == null || detail.isBlank() ? draft.getDescription() : detail);
        task.setAssigneeId(assigneeId);
        task.setPriority(priority == null || priority.isBlank() ? "P2" : priority);
        task.setStatus("TODO");
        task.setCreatedBy(operatorId);
        taskMapper.insert(task);
        operationLogService.log("TASK", task.getId(), "TASK_CREATED", operatorId, operatorName,
                "由需求草稿 #" + draft.getId() + " 建立改进任务，优先级 " + task.getPriority());
        return task.getId();
    }

    /**
     * 推进任务状态（开发/测试；PM 也可代操作）。
     *
     * <p>事件语义：START 开发 / SUBMIT 提测 / PASS 验证通过 / REJECT 验证不通过退回。
     * PASS 与 REJECT 必须填写验证结果（写入 verify_result 列 + 时间线）。
     * 非法事件与禁跳由状态机拒绝（IllegalTransitionException → 40900）。</p>
     *
     * @param taskId       任务 id
     * @param event        START/SUBMIT/PASS/REJECT
     * @param note         说明（PASS/REJECT 时为验证结果，必填）
     * @param operatorId   操作人 id
     * @param operatorName 操作人展示名
     */
    public void fire(Long taskId, String event, String note, Long operatorId, String operatorName) {
        if (!ALLOWED_EVENTS.contains(event)) {
            throw new BusinessException(FeedWiseErrorCode.TASK_EVENT_INVALID);
        }
        ImprovementTask task = requireExists(taskId);
        TaskStatus from = TaskStatus.valueOf(task.getStatus());
        List<FireArg> args = new java.util.ArrayList<>();
        if ("PASS".equals(event) || "REJECT".equals(event)) {
            if (note == null || note.isBlank()) {
                throw new BusinessException(FeedWiseErrorCode.VERIFY_RESULT_REQUIRED);
            }
            args.add(FireArg.set("verify_result", note));
        }
        taskMachine.fire(taskId, event, args.toArray(FireArg[]::new));
        operationLogService.log("TASK", taskId, "TASK_STATE_CHANGED", operatorId, operatorName,
                event + "：" + from + " → " + targetOf(event) + (note == null || note.isBlank() ? "" : "；验证/处理说明：" + note));
    }

    private String targetOf(String event) {
        return switch (event) {
            case "START" -> "IN_PROGRESS";
            case "SUBMIT" -> "PENDING_VERIFY";
            case "PASS" -> "DONE";
            case "REJECT" -> "IN_PROGRESS";
            default -> "?";
        };
    }

    /**
     * 分页查询任务。DEV 角色由 data-scope 过滤为指派给自己的任务，PM/SUPPORT 全量。
     */
    public Page<ImprovementTask> page(Page<ImprovementTask> page, String status, Long assigneeId) {
        LambdaQueryWrapper<ImprovementTask> qw = Wrappers.lambdaQuery(ImprovementTask.class)
                .eq(status != null && !status.isBlank(), ImprovementTask::getStatus, status)
                .eq(assigneeId != null, ImprovementTask::getAssigneeId, assigneeId)
                .orderByDesc(ImprovementTask::getId);
        return taskMapper.selectScoped(page, qw);
    }

    /**
     * 任务详情 + 归属校验：DEV 只能查看指派给自己的任务。
     */
    public ImprovementTask getOwned(Long taskId, Long viewerId, String viewerRole) {
        ImprovementTask task = requireExists(taskId);
        if ("DEV".equals(viewerRole) && (task.getAssigneeId() == null || !task.getAssigneeId().equals(viewerId))) {
            throw new BusinessException(FeedWiseErrorCode.DATA_NOT_OWNED);
        }
        return task;
    }

    /** @return 任务（不存在抛 40404）。用条件查询而非 selectById：state-kit CAS 直写 DB，不走缓存才能读到最新状态 */
    public ImprovementTask requireExists(Long taskId) {
        ImprovementTask task = taskMapper.selectOne(new LambdaQueryWrapper<ImprovementTask>()
                .eq(ImprovementTask::getId, taskId));
        if (task == null) {
            throw new BusinessException(FeedWiseErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /** @return 可指派的开发/测试用户列表（PM 指派用） */
    public List<DevUser> listDevUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<com.feedwise.entity.User>()
                        .eq(com.feedwise.entity.User::getRole, "DEV")).stream()
                .map(u -> new DevUser(u.getId(), u.getDisplayName())).toList();
    }

    /** 指派对象。 */
    public record DevUser(Long id, String displayName) {
    }
}

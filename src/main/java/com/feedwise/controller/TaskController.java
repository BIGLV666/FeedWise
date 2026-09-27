package com.feedwise.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.CurrentUser;
import com.feedwise.entity.ImprovementTask;
import com.feedwise.service.FeedbackService;
import com.feedwise.service.ImprovementTaskService;
import com.feedwise.service.OperationLogService;
import io.github.biglv666.authkit.annotation.RequireRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 改进任务：查询 / 状态流转（START/SUBMIT/PASS/REJECT，禁跳由状态机拒绝）。 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final ImprovementTaskService improvementTaskService;
    private final FeedbackService feedbackService;
    private final OperationLogService operationLogService;

    public TaskController(ImprovementTaskService improvementTaskService, FeedbackService feedbackService,
                          OperationLogService operationLogService) {
        this.improvementTaskService = improvementTaskService;
        this.feedbackService = feedbackService;
        this.operationLogService = operationLogService;
    }

    /** 状态流转请求体。 */
    public record FireRequest(@NotBlank String event, String note) {
    }

    /**
     * 分页查询任务（DEV 由 data-scope 只见指派给自己的任务）。
     */
    @GetMapping
    public Page<ImprovementTask> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String status) {
        return improvementTaskService.page(new Page<>(current, size), status,
                "DEV".equals(CurrentUser.role()) ? CurrentUser.id() : null);
    }

    /**
     * 任务详情（DEV 归属校验）+ 时间线（含 state-kit 状态流转历史）。
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        ImprovementTask task = improvementTaskService.getOwned(id, CurrentUser.id(), CurrentUser.role());
        return Map.of(
                "task", task,
                "timeline", operationLogService.timeline("TASK", id));
    }

    /**
     * 推进任务状态（DEV/PM）。非法事件/禁跳/终态再流转 → 40900 冲突。
     */
    @PostMapping("/{id}/fire")
    @RequireRole(value = {"DEV", "PM"}, mode = io.github.biglv666.authkit.model.AuthMode.ANY)
    public void fire(@PathVariable Long id, @Valid @RequestBody FireRequest request) {
        improvementTaskService.fire(id, request.event(), request.note(),
                CurrentUser.id(), feedbackService.displayName(CurrentUser.id()));
    }
}

package com.feedwise.controller;

import com.feedwise.service.OperationLogService;
import io.github.biglv666.authkit.annotation.RequireLogin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 全局历史查询：按对象类型 + id 检索操作时间线（登录即可查）。 */
@RestController
@RequestMapping("/api/history")
@RequireLogin
public class HistoryController {

    private final OperationLogService operationLogService;

    public HistoryController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 查询对象时间线。
     *
     * @param objectType FEEDBACK / ISSUE / DRAFT / TASK
     * @param objectId   对象 id
     * @return 时间线（操作日志 + 状态机流转历史合并，倒序）
     */
    @GetMapping
    public List<OperationLogService.TimelineItem> history(@RequestParam String objectType,
                                                          @RequestParam Long objectId) {
        return operationLogService.timeline(objectType.toUpperCase(), objectId);
    }
}

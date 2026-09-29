package com.feedwise.controller;

import com.feedwise.enums.TaskStatus;
import io.github.biglv666.authkit.annotation.RequireLogin;
import io.github.biglv666.statekit.ActionDescriptor;
import io.github.biglv666.statekit.StateMachine;
import io.github.biglv666.statekit.export.StateMachineExporter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 状态机可视化与可操作视图（state-kit 0.3.0）。
 * 前端"状态机"页渲染 mermaid 图；任务详情按 availableActions 动态渲染操作按钮。
 */
@RestController
@RequestMapping("/api/machines")
@RequireLogin
public class MachinesController {

    private final StateMachineExporter taskExporter;
    private final StateMachineExporter issueExporter;
    private final StateMachineExporter draftExporter;
    private final StateMachine<TaskStatus, Long> taskMachine;

    public MachinesController(@Qualifier("taskExporter") StateMachineExporter taskExporter,
                              @Qualifier("issueExporter") StateMachineExporter issueExporter,
                              @Qualifier("draftExporter") StateMachineExporter draftExporter,
                              @Qualifier("task") StateMachine<TaskStatus, Long> taskMachine) {
        this.taskExporter = taskExporter;
        this.issueExporter = issueExporter;
        this.draftExporter = draftExporter;
        this.taskMachine = taskMachine;
    }

    /**
     * 三台状态机的声明可视化（mermaid + dot 两种格式一次返回）。
     */
    @GetMapping("/diagrams")
    public Map<String, Map<String, String>> diagrams() {
        return Map.of(
                "task", taskExporter.all(),
                "issue", issueExporter.all(),
                "draft", draftExporter.all());
    }

    /**
     * task 机器在指定任务上的"当前可操作事件"视图（事件/目标态/守卫/期望 param）。
     *
     * @param taskId 任务 id
     * @return 可操作事件描述列表；终态或不存在时为空
     */
    @GetMapping("/task/{taskId}/actions")
    public List<ActionDescriptor> availableActions(@PathVariable Long taskId) {
        return taskMachine.availableActions(taskId);
    }
}

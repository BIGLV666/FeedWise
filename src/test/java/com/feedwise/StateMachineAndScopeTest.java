package com.feedwise;

import com.feedwise.entity.ImprovementTask;
import com.feedwise.entity.RequirementDraft;
import com.feedwise.mapper.ImprovementTaskMapper;
import com.feedwise.mapper.RequirementDraftMapper;
import com.feedwise.service.ImprovementTaskService;
import com.feedwise.service.RequirementDraftService;
import com.feedwise.support.TestBase;
import io.github.biglv666.statekit.exception.IllegalTransitionException;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 状态机与行级权限测试：禁跳 40900、草稿未确认不可转任务、DEV 数据归属。
 */
class StateMachineAndScopeTest extends TestBase {

    @Autowired
    private ImprovementTaskMapper taskMapper;
    @Autowired
    private RequirementDraftMapper draftMapper;
    @Autowired
    private ImprovementTaskService taskService;
    @Autowired
    private RequirementDraftService draftService;

    private long insertTask(Long assigneeId) {
        ImprovementTask task = new ImprovementTask();
        task.setDraftId(999L);
        task.setIssueId(888L);
        task.setTitle("状态机测试任务");
        task.setAssigneeId(assigneeId);
        task.setPriority("P2");
        task.setStatus("TODO");
        task.setCreatedBy(3L);
        taskMapper.insert(task);
        return task.getId();
    }

    @Test
    void todo_to_done_is_forbidden() {
        long taskId = insertTask(4L);
        assertThatThrownBy(() -> taskService.fire(taskId, "PASS", "试图跳步", 4L, "测试"))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void reject_event_goes_back_to_in_progress() {
        long taskId = insertTask(4L);
        taskService.fire(taskId, "START", null, 4L, "测试");
        taskService.fire(taskId, "SUBMIT", null, 4L, "测试");
        // 验证不通过退回开发中
        taskService.fire(taskId, "REJECT", "验证发现上传仍然失败", 4L, "测试");
        ImprovementTask task = taskService.requireExists(taskId);
        assertThat(task.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(task.getVerifyResult()).contains("仍然失败");
    }

    @Test
    void done_is_terminal() {
        long taskId = insertTask(4L);
        taskService.fire(taskId, "START", null, 4L, "测试");
        taskService.fire(taskId, "SUBMIT", null, 4L, "测试");
        taskService.fire(taskId, "PASS", "通过", 4L, "测试");
        assertThat(taskService.requireExists(taskId).getStatus()).isEqualTo("DONE");
        assertThatThrownBy(() -> taskService.fire(taskId, "START", null, 4L, "测试"))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void unconfirmed_draft_cannot_convert_to_task() {
        RequirementDraft draft = new RequirementDraft();
        draft.setIssueId(1L);
        draft.setTitle("未确认草稿");
        draft.setBackground("b");
        draft.setDescription("d");
        draft.setAcceptance("[]");
        draft.setStatus("DRAFT");
        draft.setAiGenerated(0);
        draftMapper.insert(draft);
        assertThatThrownBy(() -> draftService.convertToTask(draft.getId(), 4L, "P2", null, 3L, "测试PM"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未确认");
    }

    @Test
    void dev_only_sees_tasks_assigned_to_him() throws Exception {
        insertTask(4L);   // 指派给 dev1(id=4)
        insertTask(99L);  // 指派给其他人
        String dev = login("dev1");
        MvcResult page = mockMvc.perform(get("/api/tasks?size=50")
                        .header("Authorization", "Bearer " + dev))
                .andReturn();
        String body = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.err.println("[DEV-TASKS] " + body);
        List<Integer> assignees = com.jayway.jsonpath.JsonPath.read(body, "$.data.records[*].assigneeId");
        assertThat(assignees).isNotEmpty().allMatch(a -> a == 4);
    }

    @Test
    void dev_cannot_read_task_of_others() throws Exception {
        long otherTask = insertTask(99L);
        String dev = login("dev1");
        MvcResult detail = mockMvc.perform(get("/api/tasks/" + otherTask)
                        .header("Authorization", "Bearer " + dev))
                .andReturn();
        assertThat(code(detail)).isEqualTo(40904);
    }
}

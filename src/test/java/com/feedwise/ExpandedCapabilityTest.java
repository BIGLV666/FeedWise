package com.feedwise;

import com.feedwise.entity.ImprovementTask;
import com.feedwise.mapper.ImprovementTaskMapper;
import com.feedwise.service.CandidateIssueService;
import com.feedwise.service.ImprovementTaskService;
import com.feedwise.service.RequirementDraftService;
import com.feedwise.support.TestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 扩展能力测试（第二轮）：
 * REPLAY 幂等语义、@RequireSafe 二级认证、deptIn 主管范围、CSV 导出、availableActions、守卫必填。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpandedCapabilityTest extends TestBase {

    @Autowired
    private CandidateIssueService candidateIssueService;
    @Autowired
    private RequirementDraftService draftService;
    @Autowired
    private ImprovementTaskService taskService;
    @Autowired
    private ImprovementTaskService improvementTaskService;
    @Autowired
    private ImprovementTaskMapper taskMapper;

    private static long issueId;
    private static long draftId;
    private static long taskId;

    @Test
    @Order(1)
    void replay_mode_replays_same_task_id_on_duplicate_convert() throws Exception {
        // 直接用服务层准备：确认问题 → 草稿 → 确认 → 转任务（第一次转换，REPLAY 结果保存）
        issueId = candidateIssueService.createCard("扩展测试-发票上传失败", "INVOICE_UPLOAD",
                "上传失败", "修复", null, List.of(3L), false, 3L, "测试PM");
        candidateIssueService.confirm(issueId, 3L, "测试PM", "确认");
        draftId = draftService.createCore(issueId, "扩展测试草稿", "背景", "说明",
                List.of("验收1"), false, 3L, "测试PM");
        draftService.confirm(draftId, 3L, "测试PM");
        taskId = draftService.convertToTask(draftId, 4L, "P1", null, 3L, "测试PM");
        assertThat(taskId).isPositive();

        // 草稿已 CONVERTED：REPLAY 语义下重复转换应重放上次 taskId（200）而非 409 拒绝
        // （必须在同一测试方法内：测试基建每个方法前清理幂等键，跨方法重放键会被清掉）
        String pm = login("pm");
        MvcResult second = mockMvc.perform(post("/api/drafts/" + draftId + "/convert")
                        .header("Authorization", "Bearer " + pm)
                        .contentType("application/json").content("{\"assigneeId\":4,\"priority\":\"P1\"}"))
                .andReturn();
        assertThat(code(second)).isZero();
        long replayedTaskId = objectMapper.readTree(second.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").asLong();
        assertThat(replayedTaskId).isEqualTo(taskId);
    }

    @Test
    @Order(3)
    void available_actions_view_reflects_state_machine() throws Exception {
        String pm = login("pm");
        MvcResult actions = mockMvc.perform(get("/api/machines/task/" + taskId + "/actions")
                        .header("Authorization", "Bearer " + pm))
                .andReturn();
        String body = actions.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("START");
    }

    @Test
    @Order(4)
    void verify_result_guard_rejects_empty_result() {
        // 守卫扩展点：PASS 不带验证结果 → 守卫抛 40003（流转被拒绝、状态不变）
        taskService.fire(taskId, "START", null, 4L, "测试开发");
        taskService.fire(taskId, "SUBMIT", null, 4L, "测试开发");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> taskService.fire(taskId, "PASS", null, 4L, "测试开发"))
                .isInstanceOf(io.github.biglv666.webcommon.exception.BusinessException.class)
                .hasMessageContaining("验证结果");
        ImprovementTask task = improvementTaskService.requireExists(taskId);
        assertThat(task.getStatus()).isEqualTo("PENDING_VERIFY");
    }

    @Test
    @Order(5)
    void merge_requires_safe_second_auth() throws Exception {
        String pm = login("pm");
        // 准备两张 PENDING_REVIEW 卡片
        long source = candidateIssueService.createCard("待合并源卡", "OTHER", "现象A", null,
                null, List.of(), false, 3L, "测试PM");
        long target = candidateIssueService.createCard("合并目标卡", "OTHER", "现象B", null,
                null, List.of(), false, 3L, "测试PM");

        // safe 态存 Redis 可能跨运行残留（之前的测试/真机 openSafe 过 pm），先显式关闭保证隔离
        mockMvc.perform(post("/api/auth/safe/close")
                        .header("Authorization", "Bearer " + pm))
                .andReturn();
        MvcResult safeState = mockMvc.perform(get("/api/auth/safe")
                        .header("Authorization", "Bearer " + pm))
                .andReturn();
        System.err.println("[SAFE-STATE] " + safeState.getResponse().getContentAsString(StandardCharsets.UTF_8));
        // 未二级认证：@RequireSafe → 40300
        MvcResult noSafe = mockMvc.perform(post("/api/issues/" + source + "/merge")
                        .header("Authorization", "Bearer " + pm)
                        .contentType("application/json").content("{\"targetId\":" + target + "}"))
                .andReturn();
        assertThat(code(noSafe)).isEqualTo(40300);

        // 开启二级认证后：放行
        MvcResult openSafe = mockMvc.perform(post("/api/auth/safe")
                        .header("Authorization", "Bearer " + pm)
                        .contentType("application/json").content("{\"username\":\"pm\",\"password\":\"123456\"}"))
                .andReturn();
        assertThat(code(openSafe)).isZero();

        MvcResult merged = mockMvc.perform(post("/api/issues/" + source + "/merge")
                        .header("Authorization", "Bearer " + pm)
                        .contentType("application/json").content("{\"targetId\":" + target + ",\"note\":\"合并同类\"}"))
                .andReturn();
        assertThat(code(merged)).isZero();
    }

    @Test
    @Order(6)
    void support_lead_sees_only_own_dept_feedbacks_via_deptIn() throws Exception {
        String lead = login("lead1");
        MvcResult page = mockMvc.perform(get("/api/feedbacks?size=50")
                        .header("Authorization", "Bearer " + lead))
                .andReturn();
        String body = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> creators = com.jayway.jsonpath.JsonPath.read(body, "$.data.records[*].createdBy");
        // 主管（反馈组）只能看到本组客服 support1(id=1) 录入的反馈，看不到客诉组 support2(id=2) 的
        assertThat(creators).isNotEmpty().allMatch(id -> id == 1);
    }

    @Test
    @Order(7)
    void csv_export_permission_and_content() throws Exception {
        // PM 与主管有 feedback:export 权限
        String pm = login("pm");
        MvcResult csv = mockMvc.perform(get("/api/feedbacks/export")
                        .header("Authorization", "Bearer " + pm))
                .andReturn();
        String content = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.getResponse().getStatus()).isEqualTo(200);
        assertThat(content).contains("ID,内容,来源,模块");
        assertThat(content).contains("INVOICE_UPLOAD");

        // 普通客服无权限 → 40300
        String support = login("support1");
        MvcResult denied = mockMvc.perform(get("/api/feedbacks/export")
                        .header("Authorization", "Bearer " + support))
                .andReturn();
        assertThat(code(denied)).isEqualTo(40300);

        // 主管导出范围收敛到本组
        String lead = login("lead1");
        MvcResult leadCsv = mockMvc.perform(get("/api/feedbacks/export")
                        .header("Authorization", "Bearer " + lead))
                .andReturn();
        String leadContent = leadCsv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(leadCsv.getResponse().getStatus()).isEqualTo(200);
        // 本组（support1=1）的数据在，客诉组（support2=2）的不在（按录入人列判断）
        assertThat(leadContent).contains(",1,");
        assertThat(leadContent).doesNotContain(",2,2026");
    }
}

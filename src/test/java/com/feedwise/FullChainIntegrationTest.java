package com.feedwise;

import com.feedwise.support.TestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 主链路集成测试：录入 → AI 整理 → PM 确认 → AI 起草 → 确认 → 转任务 → 开发推进 → 时间线完整。
 * 覆盖设计清单第 1、4、8 条验收标准。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullChainIntegrationTest extends TestBase {

    private static String supportToken;
    private static String pmToken;
    private static String devToken;

    @Test
    @Order(1)
    void step1_support_imports_feedbacks() throws Exception {
        supportToken = login("support1");
        String content = """
                {"items":[
                  {"content":"发票上传一直失败，提示服务器错误","source":"TICKET","module":"INVOICE_UPLOAD"},
                  {"content":"上传发票总是失败，换了网络也不行","source":"SURVEY","module":"INVOICE_UPLOAD"},
                  {"content":"一上传发票就报错，提交不了报销单","source":"TICKET","module":"INVOICE_UPLOAD"},
                  {"content":"上传发票总是失败，而且也看不到报销单被退回的原因","source":"TICKET","module":"INVOICE_UPLOAD"}
                ]}""";
        MvcResult result = mockMvc.perform(post("/api/feedbacks/import")
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType("application/json").content(content))
                .andReturn();
        assertThat(code(result)).isZero();
    }

    @Test
    @Order(2)
    void step2_pm_triggers_ai_extraction_and_cards_are_created() throws Exception {
        pmToken = login("pm");
        // 混合反馈（上传失败 + 退回原因不可见）应拆成两张卡片：发票卡 3 条 + 退回原因卡 1 条
        MvcResult list = mockMvc.perform(get("/api/issues")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        String body = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.setProperty("feedwise.test.beforeExtract", body); // 仅留痕，不做全量断言（其他测试类可能已建卡）

        // 只取本测试导入的反馈（种子数据保持未处理，供人工分类演示）
        MvcResult fbList = mockMvc.perform(get("/api/feedbacks?status=UNPROCESSED&size=50")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        System.err.println("[STEP2-FBLIST] " + fbList.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<Integer> unprocessed = com.jayway.jsonpath.JsonPath.read(
                fbList.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.records[*].id");
        List<Integer> importedIds = unprocessed.stream().filter(id -> id > 12).toList();
        assertThat(importedIds).hasSize(4);
        MvcResult extract = mockMvc.perform(post("/api/ai/extract")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType("application/json")
                        .content("{\"feedbackIds\":" + importedIds + "}"))
                .andReturn();
        assertThat(code(extract)).isZero();

        MvcResult after = mockMvc.perform(get("/api/issues?status=PENDING_REVIEW")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        String afterBody = after.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(afterBody).contains("发票上传失败");
        assertThat(afterBody).contains("报销单退回原因不可见");
        // 3 条纯上传失败 + 1 条混合反馈聚成一张卡；混合反馈同时拆入退回原因卡
        assertThat(afterBody).contains("（4 条反馈）");
        assertThat(afterBody).contains("（1 条反馈）");
        assertThat(afterBody).contains("已按现象拆分为独立卡片");
    }

    private long firstPendingIssueId() throws Exception {
        MvcResult list = mockMvc.perform(get("/api/issues?status=PENDING_REVIEW")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        String body = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.err.println("[STEP4-LIST] " + body);
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.records[0].id")).longValue();
    }

    /** @return 指定标题的候选问题 id（其他测试类可能建过卡，按标题精确定位） */
    private long issueIdByTitle(String titlePart) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/issues?status=PENDING_REVIEW&size=50")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        String body = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$.data.records[*].id");
        List<String> titles = com.jayway.jsonpath.JsonPath.read(body, "$.data.records[*].title");
        for (int i = 0; i < titles.size(); i++) {
            if (titles.get(i).contains(titlePart)) {
                return ids.get(i).longValue();
            }
        }
        throw new IllegalStateException("未找到标题含 " + titlePart + " 的候选问题: " + body);
    }

    @Test
    @Order(3)
    void step3_pm_confirms_issue() throws Exception {
        long issueId = issueIdByTitle("发票上传失败");
        MvcResult confirm = mockMvc.perform(post("/api/issues/" + issueId + "/confirm")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType("application/json").content("{\"note\":\"确认为真实问题\"}"))
                .andReturn();
        assertThat(code(confirm)).isZero();
    }

    @Test
    @Order(4)
    void step4_ai_drafts_requirement_and_pm_confirms() throws Exception {
        MvcResult list = mockMvc.perform(get("/api/issues?status=CONFIRMED")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        long confirmedId = ((Number) com.jayway.jsonpath.JsonPath.read(
                list.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.records[0].id")).longValue();

        MvcResult draft = mockMvc.perform(post("/api/ai/draft")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType("application/json").content("{\"issueId\":" + confirmedId + "}"))
                .andReturn();
        assertThat(code(draft)).isZero();
        long draftId = ((Number) com.jayway.jsonpath.JsonPath.read(
                draft.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.draftId")).longValue();

        // PM 编辑后确认
        MvcResult update = mockMvc.perform(put("/api/drafts/" + draftId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType("application/json").content("""
                        {"title":"改进：发票上传失败","background":"多条用户反馈上传失败",
                         "description":"修复上传链路并提示失败原因","acceptance":["上传成功率99%"]}"""))
                .andReturn();
        assertThat(code(update)).isZero();

        MvcResult confirm = mockMvc.perform(post("/api/drafts/" + draftId + "/confirm")
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        assertThat(code(confirm)).isZero();
        // 记录草稿 id 供 step5 使用
        System.setProperty("feedwise.test.draftId", String.valueOf(draftId));
        System.setProperty("feedwise.test.confirmedIssueId", String.valueOf(confirmedId));
    }

    @Test
    @Order(5)
    void step5_pm_converts_draft_to_task() throws Exception {
        long draftId = Long.parseLong(System.getProperty("feedwise.test.draftId"));
        MvcResult convert = mockMvc.perform(post("/api/drafts/" + draftId + "/convert")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType("application/json").content("{\"assigneeId\":4,\"priority\":\"P1\"}"))
                .andReturn();
        assertThat(code(convert)).isZero();
        long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(
                convert.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data")).longValue();
        System.setProperty("feedwise.test.taskId", String.valueOf(taskId));
    }

    @Test
    @Order(6)
    void step6_dev_drives_task_through_state_machine() throws Exception {
        devToken = login("dev1");
        long taskId = Long.parseLong(System.getProperty("feedwise.test.taskId"));

        // 禁跳：TODO → PASS 不允许（必须先开发再提测）
        MvcResult illegal = mockMvc.perform(post("/api/tasks/" + taskId + "/fire")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType("application/json").content("{\"event\":\"PASS\",\"note\":\"跳步验证\"}"))
                .andReturn();
        assertThat(code(illegal)).isEqualTo(40900);

        // 正常推进：START → SUBMIT → PASS（必填验证结果）
        assertThat(code(mockMvc.perform(post("/api/tasks/" + taskId + "/fire")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType("application/json").content("{\"event\":\"START\"}")).andReturn())).isZero();
        assertThat(code(mockMvc.perform(post("/api/tasks/" + taskId + "/fire")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType("application/json").content("{\"event\":\"SUBMIT\"}")).andReturn())).isZero();

        // 验证结果必填
        MvcResult noNote = mockMvc.perform(post("/api/tasks/" + taskId + "/fire")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType("application/json").content("{\"event\":\"PASS\"}"))
                .andReturn();
        assertThat(code(noNote)).isEqualTo(40003);

        MvcResult pass = mockMvc.perform(post("/api/tasks/" + taskId + "/fire")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType("application/json")
                        .content("{\"event\":\"PASS\",\"note\":\"回归通过，上传成功率达标\"}"))
                .andReturn();
        assertThat(code(pass)).isZero();

        // 终态：DONE 再流转 → 40900
        MvcResult terminal = mockMvc.perform(post("/api/tasks/" + taskId + "/fire")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType("application/json").content("{\"event\":\"START\"}"))
                .andReturn();
        assertThat(code(terminal)).isEqualTo(40900);
    }

    @Test
    @Order(7)
    void step7_timeline_is_complete_and_scoped_queries_work() throws Exception {
        long taskId = Long.parseLong(System.getProperty("feedwise.test.taskId"));
        MvcResult timeline = mockMvc.perform(get("/api/history?objectType=TASK&objectId=" + taskId)
                        .header("Authorization", "Bearer " + pmToken))
                .andReturn();
        String body = timeline.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("TASK_CREATED");
        assertThat(body).contains("TASK_STATE_CHANGED");
        assertThat(body).contains("START");
        assertThat(body).contains("PASS");

        // data-scope：support1 只看自己录入的反馈
        MvcResult scoped = mockMvc.perform(get("/api/feedbacks?size=50")
                        .header("Authorization", "Bearer " + login("support1")))
                .andReturn();
        String scopedBody = scoped.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(scopedBody).doesNotContain("只能去问审批人"); // 种子数据里 support2 的反馈(#9)不应出现
        List<Integer> creators = com.jayway.jsonpath.JsonPath.read(scopedBody, "$.data.records[*].createdBy");
        assertThat(creators).isNotEmpty().allMatch(id -> id == 1);
    }
}

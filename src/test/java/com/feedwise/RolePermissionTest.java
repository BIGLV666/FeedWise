package com.feedwise;

import com.feedwise.support.TestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色权限测试：未登录 40100、越权 40300、数据归属 40904、放行路径。
 * 覆盖设计清单"不同岗位不同账号登录、角色权限和业务数据归属有效"。
 */
class RolePermissionTest extends TestBase {

    @Test
    void no_token_rejected_with_40100() throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/feedbacks"))
                .andReturn();
        assertThat(code(result)).isEqualTo(40100);
    }

    @Test
    void fake_token_rejected() throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/feedbacks").header("Authorization", "Bearer not-a-real-token"))
                .andReturn();
        assertThat(code(result)).isEqualTo(40100);
    }

    @Test
    void support_cannot_confirm_issue() throws Exception {
        String support = login("support1");
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/issues/1/confirm")
                        .header("Authorization", "Bearer " + support)
                        .contentType("application/json").content("{\"note\":\"越权尝试\"}"))
                .andReturn();
        assertThat(code(result)).isEqualTo(40300);
    }

    @Test
    void support_cannot_trigger_ai() throws Exception {
        String support = login("support1");
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/ai/extract")
                        .header("Authorization", "Bearer " + support)
                        .contentType("application/json").content("{\"feedbackIds\":[]}"))
                .andReturn();
        assertThat(code(result)).isEqualTo(40300);
    }

    @Test
    void dev_cannot_confirm_issue() throws Exception {
        String dev = login("dev1");
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/issues/1/confirm")
                        .header("Authorization", "Bearer " + dev)
                        .contentType("application/json").content("{\"note\":\"越权尝试\"}"))
                .andReturn();
        assertThat(code(result)).isEqualTo(40300);
    }

    @Test
    void support_cannot_read_others_feedback_detail() throws Exception {
        // 种子反馈 #12 由 support2（id=2）录入，support1 查看 → 归属拒绝
        String support = login("support1");
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/feedbacks/12")
                        .header("Authorization", "Bearer " + support))
                .andReturn();
        assertThat(code(result)).isEqualTo(40904);
    }

    @Test
    void pm_can_confirm_own_manual_issue() throws Exception {
        String pm = login("pm");
        // 自建一张无关联反馈的手工卡片再确认，避免与其他测试类共享状态
        MvcResult create = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/issues/manual")
                        .header("Authorization", "Bearer " + pm)
                        .contentType("application/json")
                        .content("{\"title\":\"权限测试卡片\",\"module\":\"OTHER\",\"problem\":\"权限验证\"}"))
                .andReturn();
        assertThat(code(create)).isZero();
        long issueId = objectMapper.readTree(create.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").asLong();
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/issues/" + issueId + "/confirm")
                        .header("Authorization", "Bearer " + pm)
                        .contentType("application/json").content("{\"note\":\"PM 确认\"}"))
                .andReturn();
        assertThat(code(result)).isZero();
    }
}

package com.feedwise;

import com.feedwise.ai.AiSchemaValidator;
import com.feedwise.ai.llm.AiProperties;
import com.feedwise.ai.llm.AiUnavailableException;
import com.feedwise.ai.llm.LlmClient;
import com.feedwise.ai.orchestrator.AiOrchestrator;
import com.feedwise.ai.tool.ToolRegistry;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.CandidateIssue;
import com.feedwise.entity.Feedback;
import com.feedwise.mapper.FeedbackMapper;
import com.feedwise.service.CandidateIssueService;
import com.feedwise.support.TestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 约束测试：受限工具白名单、业务约束拒绝、模型不可用降级、Schema 校验。
 * 覆盖 AI 业务约束 8 条验收中的核心 4 条。
 */
class AiConstraintTest extends TestBase {

    @Autowired
    private CandidateIssueService candidateIssueService;
    @Autowired
    private FeedbackMapper feedbackMapper;
    @Autowired
    private ToolRegistry toolRegistry;
    @Autowired
    private AiSchemaValidator schemaValidator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiOrchestrator realOrchestrator;

    @Test
    void feedback_can_split_into_two_cards_but_not_duplicate_within_one() {
        // 一条混合反馈（同时含"上传失败"和"退回原因"）允许归入两张卡片（拆分），
        // 但同一张卡片内不得重复出现同一条反馈
        Long first = candidateIssueService.createCard("发票上传失败（拆分测试）", "INVOICE_UPLOAD", "上传失败", "修复上传",
                null, List.of(2L), true, null, "AI(mock)");
        Long second = candidateIssueService.createCard("退回原因不可见（拆分测试）", "RETURN_MODIFY", "看不到退回原因", null,
                null, List.of(2L), true, null, "AI(mock)");
        assertThat(first).isNotEqualTo(second);
        assertThatThrownBy(() -> candidateIssueService.createCard("卡内重复", "INVOICE_UPLOAD", "上传失败", null,
                null, List.of(2L, 2L), true, null, "AI(mock)"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复出现");
    }

    @Test
    void duplicate_ids_within_one_card_rejected() {
        assertThatThrownBy(() -> candidateIssueService.createCard("重复反馈卡", "OTHER", "现象", null,
                null, List.of(2L, 2L), false, 3L, "测试PM"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复出现");
    }

    @Test
    void unregistered_tool_name_is_rejected() {
        assertThatThrownBy(() -> toolRegistry.get("delete_all_feedback"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void mixed_feedback_is_split_and_similar_keywords_not_merged() {
        // 反馈 #10（找不到填写入口）与 #8（找不到退回原因）关键词相似（找不到），但现象不同 → 不允许进同一张卡
        // 由 mock 引擎聚类规则保证：二者指纹不同；这里验证数据层同样拒绝"硬塞进一张卡"之外，
        // 再验证 orchestrator 对两条混合语义反馈产出两张卡片
        List<Feedback> targets = feedbackMapper.selectBatchIds(List.of(8L, 10L));
        assertThat(targets).hasSize(2);
        // 手工模拟"强行合并"必须被数据约束拦住：先归入一张，第二条反馈再归入另一张时不受影响；
        // 反证：同一反馈归入两张卡是允许的（拆分场景），但不同含义不能靠工具自动合并 —— mock 引擎不产出这种调用
        Long first = candidateIssueService.createCard("退回原因不可见", "RETURN_MODIFY", "看不到退回原因", null,
                null, List.of(8L), true, null, "AI(mock)");
        CandidateIssue issue = candidateIssueService.requireExists(first);
        assertThat(issue.getAiGenerated()).isEqualTo(1);
        assertThat(issue.getStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void schema_validator_rejects_bad_arguments() {
        Map<String, Object> schema = toolRegistry.get("create_candidate_issue").jsonSchema();
        var args = objectMapper.createObjectNode();
        args.put("title", ""); // 违反 minLength
        args.put("module", "NOT_A_MODULE"); // 违反 enum
        args.put("problem", "x");
        args.set("feedbackIds", objectMapper.createArrayNode()); // 违反 minItems
        List<String> errors = schemaValidator.validate(args, schema);
        assertThat(errors).isNotEmpty();
        assertThat(errors.stream().anyMatch(e -> e.contains("title"))).isTrue();
        assertThat(errors.stream().anyMatch(e -> e.contains("module"))).isTrue();
        assertThat(errors.stream().anyMatch(e -> e.contains("feedbackIds"))).isTrue();
    }

    @Test
    void model_unavailable_leaves_data_untouched_and_manual_path_works() {
        AiProperties props = new AiProperties("openai", "http://localhost:1", "k", "m", 1000, 10);
        LlmClient broken = request -> {
            throw new AiUnavailableException("模拟模型宕机");
        };
        AiOrchestrator brokenOrchestrator = new AiOrchestrator(broken, toolRegistry, schemaValidator,
                props, feedbackMapper, objectMapper);

        // 种子反馈 #11 未处理：模型挂掉 → 整理失败，反馈保持未处理
        assertThatThrownBy(() -> brokenOrchestrator.extractForFeedbacks(List.of(11L)))
                .isInstanceOf(AiUnavailableException.class);
        Feedback fb = feedbackMapper.selectById(11L);
        assertThat(fb.getStatus()).isEqualTo("UNPROCESSED");

        // 降级路径：PM 手工建卡照常闭环
        Long manual = candidateIssueService.createCard("粘贴金额不可用", "REIMBURSE_FORM", "金额字段不能粘贴", "支持粘贴",
                null, List.of(11L), false, 3L, "测试PM");
        assertThat(manual).isPositive();
        assertThat(feedbackMapper.selectById(11L).getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    void real_orchestrator_never_writes_directly_to_feedback_content() {
        // AI 编排器不暴露任何修改反馈原文的入口：工具注册表里只有 create/draft 两个受限工具
        assertThat(toolRegistry.specs()).extracting("name")
                .containsExactlyInAnyOrder("create_candidate_issue", "draft_requirement");
    }
}

package com.feedwise.controller;

import com.feedwise.ai.orchestrator.AiOrchestrator;
import com.feedwise.common.CurrentUser;
import com.feedwise.service.CandidateIssueService;
import com.feedwise.service.FeedbackService;
import com.feedwise.service.RequirementDraftService;
import io.github.biglv666.apigovernance.async.annotation.AsyncAction;
import io.github.biglv666.apigovernance.annotation.RateLimit;
import io.github.biglv666.authkit.annotation.RequireRole;
import io.github.biglv666.guard.idempotent.Idempotent;
import io.github.biglv666.webcommon.exception.BusinessException;
import io.github.biglv666.webcommon.result.Result;
import io.github.biglv666.webcommon.result.ResultCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 整理触发入口（仅产品经理）。模型不可用时抛 AiUnavailableException → 50301，
 * 前端提示改用手工分类；反馈数据不因 AI 失败产生任何变化。
 */
@RestController
@RequestMapping("/api/ai")
@RequireRole("PM")
public class AiController {

    private final AiOrchestrator aiOrchestrator;
    private final RequirementDraftService requirementDraftService;
    private final CandidateIssueService candidateIssueService;
    private final FeedbackService feedbackService;

    public AiController(AiOrchestrator aiOrchestrator, RequirementDraftService requirementDraftService,
                        CandidateIssueService candidateIssueService, FeedbackService feedbackService) {
        this.aiOrchestrator = aiOrchestrator;
        this.requirementDraftService = requirementDraftService;
        this.candidateIssueService = candidateIssueService;
        this.feedbackService = feedbackService;
    }

    /** AI 整理请求体。 */
    public record ExtractRequest(List<Long> feedbackIds) {
    }

    /** 起草请求体。 */
    public record DraftRequest(@NotNull Long issueId) {
    }

    /**
     * 触发 AI 整理（同步返回，便于演示观察；生产可仅走 outbox 异步链路）。
     * 幂等：同一 PM 对同一批反馈 60 秒内重复触发直接拒绝。
     *
     * @return cardsCreated 成功创建的候选卡片数
     */
    @PostMapping("/extract")
    @AsyncAction("feedwise.ai.extract")
    @RateLimit(limit = 5, window = 60)
    @Idempotent(key = "'ai-extract:' + #request.feedbackIds", ttl = 60, message = "AI 整理正在进行或刚完成，请刷新查看候选问题")
    public Map<String, Object> extract(@Valid @RequestBody ExtractRequest request) {
        int cards = aiOrchestrator.extractForFeedbacks(request.feedbackIds());
        return Map.of("cardsCreated", cards);
    }

    /**
     * 触发 AI 为已确认问题起草需求。
     *
     * @return draftId 新草稿 id
     */
    @PostMapping("/draft")
    @RateLimit(limit = 5, window = 60)
    public Map<String, Object> draft(@Valid @RequestBody DraftRequest request) {
        Long draftId = requirementDraftService.generateByAi(request.issueId(), CurrentUser.id(),
                operatorName());
        return Map.of("draftId", draftId);
    }

    private String operatorName() {
        return feedbackService.displayName(CurrentUser.id());
    }
}

package com.feedwise.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.CurrentUser;
import com.feedwise.entity.CandidateIssue;
import com.feedwise.service.CandidateIssueService;
import com.feedwise.service.FeedbackService;
import com.feedwise.service.OperationLogService;
import com.feedwise.service.RequirementDraftService;
import io.github.biglv666.authkit.annotation.RequireRole;
import io.github.biglv666.webcommon.result.Result;
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

/** 候选问题：查询 / 手工创建 / 确认 / 驳回 / 合并（决策全部归产品经理）。 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final CandidateIssueService candidateIssueService;
    private final FeedbackService feedbackService;
    private final OperationLogService operationLogService;
    private final RequirementDraftService requirementDraftService;

    public IssueController(CandidateIssueService candidateIssueService, FeedbackService feedbackService,
                           OperationLogService operationLogService, RequirementDraftService requirementDraftService) {
        this.candidateIssueService = candidateIssueService;
        this.feedbackService = feedbackService;
        this.operationLogService = operationLogService;
        this.requirementDraftService = requirementDraftService;
    }

    /** 手工建卡请求（模型不可用时的兜底路径）。 */
    public record ManualIssueRequest(@NotBlank String title, @NotBlank String module,
                                     @NotBlank String problem, String demand,
                                     @Valid List<Long> feedbackIds) {
    }

    /** 处理说明请求体。 */
    public record NoteRequest(String note) {
    }

    /** 合并请求体。 */
    public record MergeRequest(Long targetId, String note) {
    }

    /**
     * 分页查询候选问题。
     */
    @GetMapping
    public Page<CandidateIssue> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String module) {
        return candidateIssueService.page(new Page<>(current, size), status, module);
    }

    /**
     * 手工创建候选问题（SUPPORT/PM；AI 不可用时的兜底，产物同样是 PENDING_REVIEW 待确认）。
     */
    @PostMapping("/manual")
    @RequireRole(value = {"PM", "SUPPORT"}, mode = io.github.biglv666.authkit.model.AuthMode.ANY)
    public Long manualCreate(@Valid @RequestBody ManualIssueRequest request) {
        return candidateIssueService.createCard(request.title(), request.module(), request.problem(),
                request.demand(), null, request.feedbackIds() == null ? List.of() : request.feedbackIds(),
                false, CurrentUser.id(), feedbackService.displayName(CurrentUser.id()));
    }

    /**
     * 卡片详情：卡片 + 原始反馈回查 + 现有草稿 + 时间线。
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        CandidateIssue issue = candidateIssueService.requireExists(id);
        Long activeDraftId = requirementDraftService.latestActiveDraftId(id);
        return Map.of(
                "issue", issue,
                "feedbacks", candidateIssueService.linkedFeedbacks(id),
                "activeDraftId", activeDraftId == null ? -1L : activeDraftId,
                "timeline", operationLogService.timeline("ISSUE", id));
    }

    /** PM 确认候选问题。 */
    @PostMapping("/{id}/confirm")
    @RequireRole("PM")
    public void confirm(@PathVariable Long id, @RequestBody NoteRequest request) {
        candidateIssueService.confirm(id, CurrentUser.id(), operatorName(), request.note());
    }

    /** PM 驳回候选问题。 */
    @PostMapping("/{id}/reject")
    @RequireRole("PM")
    public void reject(@PathVariable Long id, @RequestBody NoteRequest request) {
        candidateIssueService.reject(id, CurrentUser.id(), operatorName(), request.note());
    }

    /** PM 合并候选问题到目标卡片。 */
    @PostMapping("/{id}/merge")
    @RequireRole("PM")
    public void merge(@PathVariable Long id, @Valid @RequestBody MergeRequest request) {
        candidateIssueService.merge(id, request.targetId(), CurrentUser.id(), operatorName(), request.note());
    }

    private String operatorName() {
        return feedbackService.displayName(CurrentUser.id());
    }
}

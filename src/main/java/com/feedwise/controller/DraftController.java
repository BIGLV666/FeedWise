package com.feedwise.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.CurrentUser;
import com.feedwise.entity.ImprovementTask;
import com.feedwise.entity.RequirementDraft;
import com.feedwise.service.FeedbackService;
import com.feedwise.service.ImprovementTaskService;
import com.feedwise.service.OperationLogService;
import com.feedwise.service.RequirementDraftService;
import io.github.biglv666.authkit.annotation.RequirePermission;
import io.github.biglv666.authkit.annotation.RequireRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 需求草稿：起草 / 编辑 / 确认 / 转任务（AI 产物必须经 PM 确认）。 */
@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final RequirementDraftService requirementDraftService;
    private final ImprovementTaskService improvementTaskService;
    private final FeedbackService feedbackService;
    private final OperationLogService operationLogService;

    public DraftController(RequirementDraftService requirementDraftService,
                           ImprovementTaskService improvementTaskService,
                           FeedbackService feedbackService, OperationLogService operationLogService) {
        this.requirementDraftService = requirementDraftService;
        this.improvementTaskService = improvementTaskService;
        this.feedbackService = feedbackService;
        this.operationLogService = operationLogService;
    }

    /** 手工起草请求体。 */
    public record ManualDraftRequest(@NotNull Long issueId, @NotBlank String title,
                                     @NotBlank String background, @NotBlank String description,
                                     List<String> acceptance) {
    }

    /** 编辑请求体。 */
    public record UpdateDraftRequest(@NotBlank String title, @NotBlank String background,
                                     @NotBlank String description, List<String> acceptance) {
    }

    /** 转任务请求体。 */
    public record ConvertRequest(Long assigneeId, String priority, String detail) {
    }

    /** 分页查询草稿。 */
    @GetMapping
    public Page<RequirementDraft> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String status) {
        return requirementDraftService.page(new Page<>(current, size), status);
    }

    /** PM 手工起草（AI 不可用的兜底路径）。 */
    @PostMapping("/manual")
    @RequireRole("PM")
    public Long manualCreate(@Valid @RequestBody ManualDraftRequest request) {
        return requirementDraftService.createCore(request.issueId(), request.title(), request.background(),
                request.description(), request.acceptance(), false,
                CurrentUser.id(), feedbackService.displayName(CurrentUser.id()));
    }

    /** 草稿详情 + 时间线。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        RequirementDraft draft = requirementDraftService.requireExists(id);
        List<String> acceptance = parseAcceptance(draft.getAcceptance());
        return Map.of(
                "draft", draft,
                "acceptance", acceptance,
                "timeline", operationLogService.timeline("DRAFT", id));
    }

    /** PM 编辑草稿（仅 DRAFT 状态）。 */
    @PutMapping("/{id}")
    @RequireRole("PM")
    public void update(@PathVariable Long id, @Valid @RequestBody UpdateDraftRequest request) {
        requirementDraftService.update(id, request.title(), request.background(),
                request.description(), request.acceptance(),
                CurrentUser.id(), feedbackService.displayName(CurrentUser.id()));
    }

    /** PM 确认草稿（DRAFT → CONFIRMED）。 */
    @PostMapping("/{id}/confirm")
    @RequireRole("PM")
    public void confirm(@PathVariable Long id) {
        requirementDraftService.confirm(id, CurrentUser.id(), feedbackService.displayName(CurrentUser.id()));
    }

    /**
     * PM 把已确认草稿转为改进任务（幂等 + 分布式锁双保险）。
     *
     * @return 新任务 id
     */
    @PostMapping("/{id}/convert")
    @RequirePermission("draft:convert")
    public Long convert(@PathVariable Long id, @Valid @RequestBody ConvertRequest request) {
        return requirementDraftService.convertToTask(id, request.assigneeId(), request.priority(),
                request.detail(), CurrentUser.id(), feedbackService.displayName(CurrentUser.id()));
    }

    /** 可指派的开发/测试用户。 */
    @GetMapping("/dev-users")
    public List<ImprovementTaskService.DevUser> devUsers() {
        return improvementTaskService.listDevUsers();
    }

    private List<String> parseAcceptance(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
        } catch (Exception e) {
            return List.of();
        }
    }
}

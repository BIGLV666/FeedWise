package com.feedwise.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.entity.Feedback;
import com.feedwise.service.FeedbackService;
import com.feedwise.service.OperationLogService;
import io.github.biglv666.guard.idempotent.Idempotent;
import io.github.biglv666.authkit.annotation.CurrentUser;
import io.github.biglv666.authkit.annotation.RequireRole;
import io.github.biglv666.authkit.model.AuthUser;
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

/** 反馈：录入 / 批量导入 / 查询。录入者归属即数据归属。 */
@RestController
@RequestMapping("/api/feedbacks")
@RequireRole(value = {"SUPPORT", "SUPPORT_LEAD", "PM", "DEV"}, mode = io.github.biglv666.authkit.model.AuthMode.ANY)
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final OperationLogService operationLogService;

    public FeedbackController(FeedbackService feedbackService, OperationLogService operationLogService) {
        this.feedbackService = feedbackService;
        this.operationLogService = operationLogService;
    }

    /** 录入反馈（SUPPORT/PM）。 */
    public record CreateFeedbackRequest(@NotBlank String content, String source, String module, String customerTag) {
    }

    /**
     * 单条录入。
     *
     * @return 新反馈 id
     */
    @PostMapping
    @RequireRole(value = {"SUPPORT", "PM"}, mode = io.github.biglv666.authkit.model.AuthMode.ANY)
    public Long create(@Valid @RequestBody CreateFeedbackRequest request) {
        return feedbackService.create(request.content(),
                request.source() == null ? "TICKET" : request.source(),
                request.module() == null ? "OTHER" : request.module(),
                request.customerTag(), com.feedwise.common.CurrentUser.id());
    }

    /** 批量导入条目。 */
    public record ImportFeedbackRequest(@Valid List<Item> items) {

        /** 导入条目。 */
        public record Item(@NotBlank String content, String source, String module, String customerTag) {
        }
    }

    /**
     * 批量导入（@Idempotent 防重复提交：同客服 30 秒内相同请求直接拒绝）。
     *
     * @return 导入数量
     */
    @PostMapping("/import")
    @RequireRole(value = {"SUPPORT", "PM"}, mode = io.github.biglv666.authkit.model.AuthMode.ANY)
    @Idempotent(key = "'fb-import:' + #user.userIdAsLong", ttl = 30, message = "导入请求正在处理，请勿重复提交")
    public Integer importBatch(@Valid @RequestBody ImportFeedbackRequest request, @CurrentUser AuthUser user) {
        List<Feedback> items = request.items().stream().map(item -> {
            Feedback fb = new Feedback();
            fb.setContent(item.content());
            fb.setSource(item.source());
            fb.setModule(item.module());
            fb.setCustomerTag(item.customerTag());
            return fb;
        }).toList();
        return feedbackService.importBatch(items, user.getUserIdAsLong());
    }

    /**
     * 分页查询（SUPPORT 由 data-scope 只见本人录入；module/status/keyword 过滤）。
     */
    @GetMapping
    public Page<Feedback> page(@RequestParam(defaultValue = "1") long current,
                               @RequestParam(defaultValue = "10") long size,
                               @RequestParam(required = false) String module,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String keyword) {
        return feedbackService.page(new Page<>(current, size), module, status, keyword);
    }

    /**
     * 反馈详情（SUPPORT 归属校验）+ 关联候选卡片 + 时间线。
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Feedback feedback = feedbackService.getOwned(id, com.feedwise.common.CurrentUser.id(), com.feedwise.common.CurrentUser.role());
        return Map.of(
                "feedback", feedback,
                "timeline", operationLogService.timeline("FEEDBACK", id));
    }
}

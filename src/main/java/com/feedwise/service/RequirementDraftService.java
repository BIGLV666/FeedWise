package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.ai.orchestrator.AiOrchestrator;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.CandidateIssue;
import com.feedwise.entity.RequirementDraft;
import com.feedwise.enums.DraftStatus;
import com.feedwise.mapper.CandidateIssueMapper;
import com.feedwise.mapper.RequirementDraftMapper;
import io.github.biglv666.guard.lock.DistributedLock;
import io.github.biglv666.guard.idempotent.Idempotent;
import io.github.biglv666.statekit.FireArg;
import io.github.biglv666.statekit.StateMachine;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 需求草稿服务：AI 起草 / PM 手写 → PM 编辑 → PM 确认 → PM 转改进任务。
 * AI 产物只是草稿；未经 PM 确认（DRAFT→CONVERTED 属禁跳）不可能变成任务。
 * 草稿创建核心在 {@link DraftCoreService}（与 AI 工具共用，避免循环依赖）。
 */
@Service
public class RequirementDraftService {

    private static final Logger log = LoggerFactory.getLogger(RequirementDraftService.class);

    private final RequirementDraftMapper draftMapper;
    private final DraftCoreService draftCoreService;
    private final CandidateIssueMapper issueMapper;
    private final CandidateIssueService candidateIssueService;
    private final ImprovementTaskService improvementTaskService;
    private final OperationLogService operationLogService;
    private final FeedbackService feedbackService;
    private final AiOrchestrator aiOrchestrator;
    private final StateMachine<DraftStatus, Long> draftMachine;

    public RequirementDraftService(RequirementDraftMapper draftMapper, DraftCoreService draftCoreService,
                                   CandidateIssueMapper issueMapper, CandidateIssueService candidateIssueService,
                                   ImprovementTaskService improvementTaskService,
                                   OperationLogService operationLogService, FeedbackService feedbackService,
                                   AiOrchestrator aiOrchestrator,
                                   @Qualifier("draft") StateMachine<DraftStatus, Long> draftMachine) {
        this.draftMapper = draftMapper;
        this.draftCoreService = draftCoreService;
        this.issueMapper = issueMapper;
        this.candidateIssueService = candidateIssueService;
        this.improvementTaskService = improvementTaskService;
        this.operationLogService = operationLogService;
        this.feedbackService = feedbackService;
        this.aiOrchestrator = aiOrchestrator;
        this.draftMachine = draftMachine;
    }

    /**
     * AI 为已确认问题起草需求（触发入口仅限 PM）。模型不可用时抛 AiUnavailableException 由 Controller 转降级提示。
     *
     * @param issueId      已确认候选问题 id
     * @param operatorId   PM id
     * @param operatorName PM 展示名
     * @return 新草稿 id
     */
    @Transactional
    public Long generateByAi(Long issueId, Long operatorId, String operatorName) {
        CandidateIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(FeedWiseErrorCode.ISSUE_NOT_FOUND);
        }
        if (!"CONFIRMED".equals(issue.getStatus())) {
            throw new BusinessException(FeedWiseErrorCode.ISSUE_ALREADY_REVIEWED, "仅已确认的问题可以起草需求");
        }
        int feedbackCount = candidateIssueService.linkedFeedbackIds(issueId).size();
        Long draftId = aiOrchestrator.draftForIssue(issueId, issue.getTitle(), issue.getModule(),
                issue.getProblem(), issue.getDemand() == null ? "" : issue.getDemand(), feedbackCount);
        if (draftId == null) {
            throw new BusinessException(FeedWiseErrorCode.AI_UNAVAILABLE, "AI 本轮未产出草稿，请改用手工起草");
        }
        operationLogService.log("ISSUE", issueId, "ISSUE_AI_DRAFTED", operatorId, operatorName,
                "AI 生成需求草稿 #" + draftId + "，待确认");
        return draftId;
    }

    /**
     * 创建需求草稿（委托 {@link DraftCoreService#createCore}，AI 工具与手工共用同一约束）。
     */
    @Transactional
    public Long createCore(Long issueId, String title, String background, String description,
                           List<String> acceptance, boolean aiGenerated, Long createdBy, String operatorName) {
        return draftCoreService.createCore(issueId, title, background, description, acceptance,
                aiGenerated, createdBy, operatorName);
    }

    /** @return 该问题当前的活动草稿 id，无则 null（委托 DraftCoreService） */
    public Long latestActiveDraftId(Long issueId) {
        return draftCoreService.latestActiveDraftId(issueId);
    }

    /**
     * PM 编辑草稿（仅 DRAFT 状态可编辑，确认后锁定）。
     */
    @Transactional
    public void update(Long draftId, String title, String background, String description, List<String> acceptance,
                       Long operatorId, String operatorName) {
        RequirementDraft draft = requireExists(draftId);
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BusinessException(FeedWiseErrorCode.DRAFT_EXISTS, "草稿已确认/已转任务，禁止编辑");
        }
        RequirementDraft patch = new RequirementDraft();
        patch.setId(draftId);
        patch.setTitle(title);
        patch.setBackground(background);
        patch.setDescription(description);
        patch.setAcceptance(draftCoreService.toJson(acceptance));
        draftMapper.updateById(patch);
        operationLogService.log("DRAFT", draftId, "DRAFT_UPDATED", operatorId, operatorName, "产品经理修改草稿内容");
    }

    /**
     * PM 确认草稿（状态机：DRAFT --CONFIRM--> CONFIRMED）。
     */
    public void confirm(Long draftId, Long operatorId, String operatorName) {
        requireExists(draftId);
        draftMachine.fire(draftId, "CONFIRM", FireArg.set("confirmed_by", operatorId));
        operationLogService.log("DRAFT", draftId, "DRAFT_CONFIRMED", operatorId, operatorName, "产品经理确认需求草稿");
    }

    /**
     * PM 把已确认草稿转为改进任务。
     *
     * <p>并发防护双保险：@Idempotent 拒绝同一草稿的重复转换请求；@DistributedLock 保证并发安全。
     * 转换内部：创建 TODO 任务 + 状态机 DRAFT --CONVERT--> CONVERTED。</p>
     *
     * @param draftId      已确认草稿 id
     * @param assigneeId   指派开发（可空，后续可指派）
     * @param priority     优先级 P1/P2/P3（仅 PM 可定）
     * @param detail       任务补充说明
     * @param operatorId   PM id
     * @param operatorName PM 展示名
     * @return 新任务 id
     */
    @Idempotent(key = "'draft-convert:' + #draftId", ttl = 30, message = "该草稿正在转换或已转换过，请刷新查看任务列表")
    @DistributedLock(key = "'draft-convert:' + #draftId", waitTime = 3)
    @Transactional
    public Long convertToTask(Long draftId, Long assigneeId, String priority, String detail,
                              Long operatorId, String operatorName) {
        RequirementDraft draft = requireExists(draftId);
        if (!"CONFIRMED".equals(draft.getStatus())) {
            // 状态机同样会拒绝（禁跳），这里提前给出更友好的错误信息
            throw new BusinessException(FeedWiseErrorCode.DRAFT_EXISTS, "草稿未确认，不能建立改进任务");
        }
        Long taskId = improvementTaskService.createFromDraft(draft, assigneeId, priority, detail, operatorId, operatorName);
        draftMachine.fire(draftId, "CONVERT", FireArg.set("confirmed_by", operatorId));
        operationLogService.log("DRAFT", draftId, "DRAFT_CONVERTED", operatorId, operatorName,
                "草稿转为改进任务 #" + taskId);
        return taskId;
    }

    /**
     * 分页查询草稿。
     */
    public Page<RequirementDraft> page(Page<RequirementDraft> page, String status) {
        return draftMapper.selectPage(page, new LambdaQueryWrapper<RequirementDraft>()
                .eq(status != null && !status.isBlank(), RequirementDraft::getStatus, status)
                .orderByDesc(RequirementDraft::getId));
    }

    /** @return 草稿（不存在抛 40403） */
    public RequirementDraft requireExists(Long draftId) {
        // 条件查询不缓存：draft 状态由 state-kit CAS 直写 DB，selectById 有脏缓存风险
        RequirementDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<RequirementDraft>()
                .eq(RequirementDraft::getId, draftId));
        if (draft == null) {
            throw new BusinessException(FeedWiseErrorCode.DRAFT_NOT_FOUND);
        }
        return draft;
    }
}

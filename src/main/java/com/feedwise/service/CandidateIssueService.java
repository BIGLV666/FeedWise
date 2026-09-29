package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.CandidateIssue;
import com.feedwise.entity.Feedback;
import com.feedwise.entity.IssueFeedbackLink;
import com.feedwise.mapper.CandidateIssueMapper;
import com.feedwise.mapper.FeedbackMapper;
import com.feedwise.enums.IssueStatus;
import com.feedwise.mapper.IssueFeedbackLinkMapper;
import io.github.biglv666.cachekit.core.CacheKit;
import io.github.biglv666.guard.lock.LockTemplate;
import io.github.biglv666.statekit.FireArg;
import io.github.biglv666.statekit.StateMachine;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 候选问题服务：创建（AI 工具 / PM 手工共用同一核心）、确认、驳回、合并。
 * 状态流转全部经 state-kit issue 状态机（CAS 唯一写入口，禁跳抛 40900）。
 */
@Service
public class CandidateIssueService {

    private final CandidateIssueMapper issueMapper;
    private final FeedbackMapper feedbackMapper;
    private final IssueFeedbackLinkMapper linkMapper;
    private final OperationLogService operationLogService;
    private final FeedbackService feedbackService;
    private final StateMachine<IssueStatus, Long> issueMachine;
    /** 编程式分布式锁：合并候选问题用（与 @DistributedLock 注解式对照展示） */
    private final LockTemplate lockTemplate;

    public CandidateIssueService(CandidateIssueMapper issueMapper, FeedbackMapper feedbackMapper,
                                 IssueFeedbackLinkMapper linkMapper, OperationLogService operationLogService,
                                 FeedbackService feedbackService,
                                 @Qualifier("issue") StateMachine<IssueStatus, Long> issueMachine,
                                 LockTemplate lockTemplate) {
        this.issueMapper = issueMapper;
        this.feedbackMapper = feedbackMapper;
        this.linkMapper = linkMapper;
        this.operationLogService = operationLogService;
        this.feedbackService = feedbackService;
        this.issueMachine = issueMachine;
        this.lockTemplate = lockTemplate;
    }

    /**
     * 创建候选问题卡片核心（AI 工具与 PM 手工共用；事务边界由调用方决定）。
     *
     * <p>业务约束：反馈必须存在；同一反馈不得在同一张卡片内重复出现；
     * 同一反馈可以归入多张卡片（一条反馈含多个问题的拆分场景）；
     * AI 产物固定 PENDING_REVIEW + aiGenerated=1。</p>
     *
     * @param title       问题标题
     * @param module      功能模块
     * @param problem     问题现象归纳
     * @param demand      用户诉求归纳
     * @param similarNote 疑似相似提示（AI 给 PM 的提示，可为 null）
     * @param feedbackIds 关联反馈 id
     * @param aiGenerated 是否 AI 生成
     * @param createdBy   创建人（AI 为 null）
     * @param operatorName 操作人展示名（时间线）
     * @return 新卡片 id
     * @throws BusinessException 反馈不存在 / 已处理 / 入参重复
     */
    @Transactional
    public Long createCard(String title, String module, String problem, String demand, String similarNote,
                           List<Long> feedbackIds, boolean aiGenerated, Long createdBy, String operatorName) {
        if (title == null || title.isBlank() || problem == null || problem.isBlank()) {
            throw new BusinessException(FeedWiseErrorCode.AI_TOOL_REJECTED, "标题与问题现象不能为空");
        }
        List<Long> distinctIds = List.copyOf(new LinkedHashSet<>(feedbackIds));
        if (distinctIds.size() != feedbackIds.size()) {
            throw new BusinessException(FeedWiseErrorCode.AI_TOOL_REJECTED, "同一反馈不能在一张卡片内重复出现");
        }
        List<Feedback> feedbacks = distinctIds.isEmpty()
                ? List.of()
                : feedbackMapper.selectBatchIds(distinctIds);
        if (feedbacks.size() != distinctIds.size()) {
            throw new BusinessException(FeedWiseErrorCode.AI_TOOL_REJECTED, "部分反馈不存在: " + distinctIds);
        }

        CandidateIssue issue = new CandidateIssue();
        issue.setTitle(title);
        issue.setModule(module);
        issue.setProblem(problem);
        issue.setDemand(demand);
        issue.setSimilarNote(similarNote);
        issue.setStatus("PENDING_REVIEW");
        issue.setAiGenerated(aiGenerated ? 1 : 0);
        issue.setCreatedBy(createdBy);
        issueMapper.insert(issue);

        for (Long feedbackId : distinctIds) {
            IssueFeedbackLink link = new IssueFeedbackLink();
            link.setIssueId(issue.getId());
            link.setFeedbackId(feedbackId);
            linkMapper.insert(link);
            // 反馈原文不变，只推进处理状态：被关联即视为已归档（已能回查原文）
            Feedback fb = new Feedback();
            fb.setId(feedbackId);
            fb.setStatus("PROCESSED");
            feedbackMapper.updateById(fb);
        }
        operationLogService.log("ISSUE", issue.getId(), aiGenerated ? "ISSUE_AI_CREATED" : "ISSUE_MANUAL_CREATED",
                createdBy, operatorName, "创建候选问题，关联反馈 " + distinctIds + " 条（待产品经理确认）");
        for (Long feedbackId : distinctIds) {
            operationLogService.log("FEEDBACK", feedbackId, "FEEDBACK_LINKED",
                    createdBy, operatorName, "归入候选问题 #" + issue.getId() + "《" + title + "》");
        }
        return issue.getId();
    }

    /**
     * PM 确认候选问题（唯一能进入 CONFIRMED 的路径）。
     *
     * @param issueId    卡片 id
     * @param operatorId PM id
     * @param operatorName PM 展示名
     * @param note       处理说明
     */
    public void confirm(Long issueId, Long operatorId, String operatorName, String note) {
        issueMachine.fire(issueId, "CONFIRM",
                FireArg.set("confirmed_by", operatorId),
                FireArg.set("confirm_note", note == null ? "确认成立" : note));
        operationLogService.log("ISSUE", issueId, "ISSUE_CONFIRMED", operatorId, operatorName,
                note == null ? "确认问题成立" : note);
    }

    /**
     * PM 驳回候选问题（AI 误判时使用，关联反馈保留原文与时间线）。
     */
    public void reject(Long issueId, Long operatorId, String operatorName, String note) {
        issueMachine.fire(issueId, "REJECT",
                FireArg.set("confirmed_by", operatorId),
                FireArg.set("confirm_note", note == null ? "驳回" : note));
        operationLogService.log("ISSUE", issueId, "ISSUE_REJECTED", operatorId, operatorName,
                note == null ? "驳回候选问题" : note);
    }

    /**
     * PM 合并候选问题到另一张已确认的卡片。
     *
     * <p>合并是 PM 的专属决策：AI 无合并工具。源卡片状态置 MERGED 并记录目标，
     * 原有关联保留以便回查。</p>
     *
     * @param sourceId 源卡片（必须 PENDING_REVIEW）
     * @param targetId 目标卡片（必须 CONFIRMED）
     */
    public void merge(Long sourceId, Long targetId, Long operatorId, String operatorName, String note) {
        // 编程式分布式锁：同一源卡片的合并互斥（注解式 @DistributedLock 的对应写法）
        lockTemplate.withLock("issue-merge:" + sourceId, () -> doMerge(sourceId, targetId, operatorId, operatorName, note));
    }

    private void doMerge(Long sourceId, Long targetId, Long operatorId, String operatorName, String note) {
        // withDb 强一致读：合并决策不能被缓存旧状态误导（cache-kit 旁路演示）
        CandidateIssue target = CacheKit.withDb(() -> issueMapper.selectById(targetId));
        if (target == null || target.getMergedIntoId() != null) {
            throw new BusinessException(FeedWiseErrorCode.MERGE_TARGET_INVALID);
        }
        if (!"CONFIRMED".equals(target.getStatus()) && !"PENDING_REVIEW".equals(target.getStatus())) {
            throw new BusinessException(FeedWiseErrorCode.MERGE_TARGET_INVALID, "合并目标已驳回，不可合并");
        }
        issueMachine.fire(sourceId, "MERGE",
                FireArg.set("merged_into_id", targetId),
                FireArg.set("confirmed_by", operatorId),
                FireArg.set("confirm_note", note == null ? "合并入 #" + targetId : note));
        operationLogService.log("ISSUE", sourceId, "ISSUE_MERGED", operatorId, operatorName,
                "合并入候选问题 #" + targetId + (note == null ? "" : "；" + note));
        operationLogService.log("ISSUE", targetId, "ISSUE_ABSORBED", operatorId, operatorName,
                "候选问题 #" + sourceId + " 并入本卡片");
    }

    /**
     * 分页查询候选问题。
     *
     * @param status 状态过滤，可空
     */
    public Page<CandidateIssue> page(Page<CandidateIssue> page, String status, String module) {
        return issueMapper.selectPage(page, new LambdaQueryWrapper<CandidateIssue>()
                .eq(status != null && !status.isBlank(), CandidateIssue::getStatus, status)
                .eq(module != null && !module.isBlank(), CandidateIssue::getModule, module)
                .orderByDesc(CandidateIssue::getId));
    }

    /**
     * 卡片详情（含归属校验：仅 SUPPORT 限制本人反馈链路上的卡片，PM/DEV 全量）。
     *
     * @param id 卡片 id
     * @return 卡片实体
     */
    public CandidateIssue requireExists(Long id) {
        // 条件查询不缓存：issue 状态由 state-kit CAS 直写 DB，selectById 有脏缓存风险
        CandidateIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<CandidateIssue>()
                .eq(CandidateIssue::getId, id));
        if (issue == null) {
            throw new BusinessException(FeedWiseErrorCode.ISSUE_NOT_FOUND);
        }
        return issue;
    }

    /** @return 卡片关联的原始反馈（按关联时间正序），供原文回查 */
    public List<Feedback> linkedFeedbacks(Long issueId) {
        List<IssueFeedbackLink> links = linkMapper.selectList(new LambdaQueryWrapper<IssueFeedbackLink>()
                .eq(IssueFeedbackLink::getIssueId, issueId).orderByAsc(IssueFeedbackLink::getId));
        return links.stream().map(l -> feedbackMapper.selectById(l.getFeedbackId())).toList();
    }

    /** @return 卡片关联的反馈 id */
    public List<Long> linkedFeedbackIds(Long issueId) {
        return linkMapper.selectList(new LambdaQueryWrapper<IssueFeedbackLink>()
                .eq(IssueFeedbackLink::getIssueId, issueId)).stream().map(IssueFeedbackLink::getFeedbackId).toList();
    }
}

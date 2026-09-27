package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.CandidateIssue;
import com.feedwise.entity.RequirementDraft;
import com.feedwise.mapper.CandidateIssueMapper;
import com.feedwise.mapper.RequirementDraftMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 需求草稿创建核心：AI 工具（DraftRequirementTool）与 PM 手工路径共用。
 * 独立成类以避免"编排器→工具→草稿服务→编排器"的循环依赖。
 */
@Service
public class DraftCoreService {

    private static final Logger log = LoggerFactory.getLogger(DraftCoreService.class);

    private final RequirementDraftMapper draftMapper;
    private final CandidateIssueMapper issueMapper;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public DraftCoreService(RequirementDraftMapper draftMapper, CandidateIssueMapper issueMapper,
                            OperationLogService operationLogService, ObjectMapper objectMapper) {
        this.draftMapper = draftMapper;
        this.issueMapper = issueMapper;
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建需求草稿核心。
     *
     * <p>业务约束：问题必须存在且 CONFIRMED；一个问题同时只允许一张活动草稿（CONVERTED 后允许重写）。</p>
     *
     * @param issueId      已确认候选问题 id
     * @param title        草稿标题
     * @param background   背景说明
     * @param description  需求说明
     * @param acceptance   验收条件列表
     * @param aiGenerated  是否 AI 生成
     * @param createdBy    创建人（AI 为 null）
     * @param operatorName 操作人展示名（时间线）
     * @return 新草稿 id
     * @throws BusinessException 问题不存在 / 未确认 / 已有活动草稿
     */
    @Transactional
    public Long createCore(Long issueId, String title, String background, String description,
                           List<String> acceptance, boolean aiGenerated, Long createdBy, String operatorName) {
        CandidateIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(FeedWiseErrorCode.ISSUE_NOT_FOUND);
        }
        if (!"CONFIRMED".equals(issue.getStatus())) {
            throw new BusinessException(FeedWiseErrorCode.ISSUE_ALREADY_REVIEWED,
                    "问题 #" + issueId + " 未确认或已驳回/合并，不能起草需求");
        }
        Long existing = latestActiveDraftId(issueId);
        if (existing != null) {
            throw new BusinessException(FeedWiseErrorCode.DRAFT_EXISTS, "该问题已有草稿 #" + existing);
        }
        RequirementDraft draft = new RequirementDraft();
        draft.setIssueId(issueId);
        draft.setTitle(title);
        draft.setBackground(background);
        draft.setDescription(description);
        draft.setAcceptance(toJson(acceptance));
        draft.setStatus("DRAFT");
        draft.setAiGenerated(aiGenerated ? 1 : 0);
        draft.setCreatedBy(createdBy);
        draftMapper.insert(draft);
        operationLogService.log("DRAFT", draft.getId(), aiGenerated ? "DRAFT_AI_GENERATED" : "DRAFT_MANUAL_CREATED",
                createdBy, operatorName, "生成需求草稿，关联问题 #" + issueId + "（待产品经理确认）");
        return draft.getId();
    }

    /** @return 该问题当前的活动草稿 id（未 CONVERTED 的最新一张），无则 null */
    public Long latestActiveDraftId(Long issueId) {
        RequirementDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<RequirementDraft>()
                .eq(RequirementDraft::getIssueId, issueId)
                .ne(RequirementDraft::getStatus, "CONVERTED")
                .orderByDesc(RequirementDraft::getId)
                .last("LIMIT 1"));
        return draft == null ? null : draft.getId();
    }

    /** 验收条件列表 → JSON 数组字符串。 */
    public String toJson(List<String> acceptance) {
        if (acceptance == null || acceptance.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(acceptance);
        } catch (Exception e) {
            log.warn("验收条件序列化失败", e);
            return "[]";
        }
    }
}

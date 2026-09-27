package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 候选问题卡片：AI 或 PM 手工创建，状态流转由 state-kit issue 状态机唯一写入口。 */
@Data
@TableName("candidate_issue")
public class CandidateIssue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String module;

    /** 问题现象归纳 */
    private String problem;

    /** 用户诉求归纳 */
    private String demand;

    private String status;

    /** MERGED 时指向目标卡片 */
    private Long mergedIntoId;

    /** 1=AI 生成（PENDING_REVIEW 待 PM 确认） */
    private Integer aiGenerated;

    /** AI 给 PM 的疑似相似提示（AI 本身不做合并决策） */
    private String similarNote;

    private Long createdBy;

    private Long confirmedBy;

    /** 处理说明（确认/驳回/合并时填写） */
    private String confirmNote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.feedwise.enums;

/**
 * 候选问题卡片状态（state-kit issue 状态机）。
 * 仅允许 PENDING_REVIEW 出边：CONFIRM/REJECT/MERGE，其余流转一律禁跳。
 */
public enum IssueStatus {
    PENDING_REVIEW,
    CONFIRMED,
    REJECTED,
    /** 已合并入其他卡片（merged_into_id 指向目标） */
    MERGED
}

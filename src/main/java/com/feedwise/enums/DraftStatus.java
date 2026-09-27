package com.feedwise.enums;

/**
 * 需求草稿状态（state-kit draft 状态机）。
 * DRAFT→CONVERTED 属于禁跳：必须先由 PM 确认。
 */
public enum DraftStatus {
    DRAFT,
    CONFIRMED,
    /** 已转为改进任务 */
    CONVERTED
}

package com.feedwise.enums;

/**
 * 改进任务状态（state-kit task 状态机）。
 * 合法流转：TODO→IN_PROGRESS→PENDING_VERIFY→DONE；
 * PENDING_VERIFY 可 REJECT 退回 IN_PROGRESS。TODO→DONE 等属禁跳。
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    PENDING_VERIFY,
    DONE
}

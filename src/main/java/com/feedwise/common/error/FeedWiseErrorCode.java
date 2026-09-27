package com.feedwise.common.error;

import io.github.biglv666.webcommon.result.ErrorCode;

/**
 * FeedWise 业务错误码（web-common 分段：404xx 不存在 / 409xx 冲突 / 503xx 依赖不可用）。
 * 经 @ErrorCodeScan 注册，错误码字典端点 /web-common/error-codes 可查。
 */
public enum FeedWiseErrorCode implements ErrorCode {

    /** 反馈不存在 */
    FEEDBACK_NOT_FOUND(40401, "反馈不存在"),
    /** 候选问题不存在 */
    ISSUE_NOT_FOUND(40402, "候选问题不存在"),
    /** 需求草稿不存在 */
    DRAFT_NOT_FOUND(40403, "需求草稿不存在"),
    /** 改进任务不存在 */
    TASK_NOT_FOUND(40404, "改进任务不存在"),
    /** 用户不存在 */
    USER_NOT_FOUND(40405, "用户不存在"),

    /** 反馈已被其他候选问题关联，AI/人工均不得重复归入 */
    FEEDBACK_ALREADY_LINKED(40901, "反馈已归入其他候选问题"),
    /** 候选问题已处理，不允许重复确认/驳回/合并 */
    ISSUE_ALREADY_REVIEWED(40902, "该候选问题已处理，禁止重复操作"),
    /** 一个候选问题同时只允许一张活动草稿 */
    DRAFT_EXISTS(40903, "该问题已有需求草稿"),
    /** 无权限执行该操作（补充语义，auth-kit 40300 之外的归属类拒绝） */
    DATA_NOT_OWNED(40904, "无权访问该数据"),
    /** 合并目标不可用（不存在或未确认） */
    MERGE_TARGET_INVALID(40905, "合并目标不存在或未确认"),

    /** 重复提交被幂等组件拒绝 */
    DUPLICATE_REQUEST(40906, "请勿重复提交，请稍后刷新查看结果"),

    /** AI 工具调用被业务约束拒绝 */
    AI_TOOL_REJECTED(40001, "AI 调用被业务约束拒绝"),
    /** AI 模型不可用，反馈保持未处理，可改走手工分类 */
    AI_UNAVAILABLE(50301, "AI 模型暂不可用，请改用手工分类"),

    /** 用户名或密码错误 */
    BAD_CREDENTIALS(40101, "用户名或密码错误"),

    /** 非法的任务状态事件（不在 START/SUBMIT/PASS/REJECT 之内） */
    TASK_EVENT_INVALID(40002, "非法的任务状态事件"),
    /** 验证通过/不通过必须填写验证结果 */
    VERIFY_RESULT_REQUIRED(40003, "验证结果说明为必填项");

    private final int code;
    private final String message;

    FeedWiseErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

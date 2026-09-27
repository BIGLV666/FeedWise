package com.feedwise.ai.tool;

/**
 * 受限工具执行结果。
 *
 * @param success 是否执行成功；失败时 message 说明被哪条业务约束拒绝
 * @param message 结果说明（成功时用于时间线，失败时用于 AI 修正与日志）
 * @param data    成功产物（如新建卡片 id）
 */
public record ToolResult(boolean success, String message, Object data) {

    public static ToolResult ok(String message, Object data) {
        return new ToolResult(true, message, data);
    }

    public static ToolResult reject(String message) {
        return new ToolResult(false, message, null);
    }
}

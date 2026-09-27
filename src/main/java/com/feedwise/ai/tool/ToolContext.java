package com.feedwise.ai.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 AI 整理运行的上下文。
 *
 * <p>aiName 写入操作时间线（如 "AI(mock)"）；results 收集本轮成功工具的产物
 * （如新建卡片/草稿 id），供编排器调用方读取。</p>
 */
public class ToolContext {

    private final String aiName;
    private final String batchId;
    private final List<ToolResult> results = new ArrayList<>();

    public ToolContext(String aiName, String batchId) {
        this.aiName = aiName;
        this.batchId = batchId;
    }

    public String aiName() {
        return aiName;
    }

    public String batchId() {
        return batchId;
    }

    /** 工具执行成功后由编排器登记产物。 */
    public void record(ToolResult result) {
        if (result != null && result.success()) {
            results.add(result);
        }
    }

    public List<ToolResult> results() {
        return results;
    }
}

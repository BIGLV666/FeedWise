package com.feedwise.entity;

import io.github.biglv666.cachekit.annotation.CacheEntity;
import io.github.biglv666.cachekit.annotation.CacheId;

import java.util.Map;

/**
 * 工作台统计快照（cache-kit @CacheEntity 演示：非 MP 实体也可缓存）。
 * ttl=30 秒：统计类数据允许短窗口陈旧，换来计算零开销。
 */
@CacheEntity(prefix = "dashboard_snapshot", ttl = 30)
public class DashboardSnapshot {

    /** 固定主键：工作台只有一份全局统计快照 */
    @CacheId
    private String snapshotId;

    private Map<String, Long> feedbackByStatus;
    private Map<String, Long> feedbackByModule;
    private Map<String, Long> issueByStatus;
    private Map<String, Long> taskByStatus;

    public DashboardSnapshot() {
    }

    public DashboardSnapshot(String snapshotId, Map<String, Long> feedbackByStatus,
                             Map<String, Long> feedbackByModule, Map<String, Long> issueByStatus,
                             Map<String, Long> taskByStatus) {
        this.snapshotId = snapshotId;
        this.feedbackByStatus = feedbackByStatus;
        this.feedbackByModule = feedbackByModule;
        this.issueByStatus = issueByStatus;
        this.taskByStatus = taskByStatus;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Map<String, Long> getFeedbackByStatus() {
        return feedbackByStatus;
    }

    public void setFeedbackByStatus(Map<String, Long> feedbackByStatus) {
        this.feedbackByStatus = feedbackByStatus;
    }

    public Map<String, Long> getFeedbackByModule() {
        return feedbackByModule;
    }

    public void setFeedbackByModule(Map<String, Long> feedbackByModule) {
        this.feedbackByModule = feedbackByModule;
    }

    public Map<String, Long> getIssueByStatus() {
        return issueByStatus;
    }

    public void setIssueByStatus(Map<String, Long> issueByStatus) {
        this.issueByStatus = issueByStatus;
    }

    public Map<String, Long> getTaskByStatus() {
        return taskByStatus;
    }

    public void setTaskByStatus(Map<String, Long> taskByStatus) {
        this.taskByStatus = taskByStatus;
    }
}

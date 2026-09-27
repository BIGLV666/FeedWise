package com.feedwise.integration.outbox;

import java.util.List;

/**
 * 反馈批量导入事件载荷（事务 outbox 模式：业务写入与事件同事务落库）。
 *
 * @param feedbackIds 本批反馈 id
 * @param operatorId  导入操作人
 */
public record FeedbackBatchImportedEvent(List<Long> feedbackIds, Long operatorId) {
}

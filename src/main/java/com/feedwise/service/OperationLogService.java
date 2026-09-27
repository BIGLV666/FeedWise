package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feedwise.entity.OperationLog;
import com.feedwise.mapper.OperationLogMapper;
import io.github.biglv666.statekit.history.HistoryEntry;
import io.github.biglv666.statekit.history.HistoryQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 操作时间线服务。关键操作必须留下时间、操作人和处理说明；
 * state-kit 状态机自身的流转历史（sk_transition_history）合并进同一时间线查询。
 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final HistoryQueryService historyQueryService;

    public OperationLogService(OperationLogMapper operationLogMapper, HistoryQueryService historyQueryService) {
        this.operationLogMapper = operationLogMapper;
        this.historyQueryService = historyQueryService;
    }

    /**
     * 记录一条操作日志。
     *
     * @param objectType   对象类型 FEEDBACK/ISSUE/DRAFT/TASK/AI_RUN
     * @param objectId     对象 id
     * @param action       动作编码
     * @param operatorId   操作人 id（AI 操作为 null）
     * @param operatorName 操作人展示名
     * @param detail       处理说明
     */
    public void log(String objectType, Long objectId, String action, Long operatorId, String operatorName, String detail) {
        OperationLog entry = new OperationLog();
        entry.setObjectType(objectType);
        entry.setObjectId(objectId);
        entry.setAction(action);
        entry.setOperatorId(operatorId);
        entry.setOperatorName(operatorName);
        entry.setDetail(detail);
        operationLogMapper.insert(entry);
    }

    /**
     * 查询某对象的时间线：业务操作日志 + 状态机流转历史合并，按时间倒序。
     *
     * @param objectType 对象类型（同时用作 state-kit machine 名小写映射）
     * @param objectId   对象 id
     * @return 时间线条目
     */
    public List<TimelineItem> timeline(String objectType, Long objectId) {
        List<TimelineItem> items = new ArrayList<>();
        List<OperationLog> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getObjectType, objectType)
                .eq(OperationLog::getObjectId, objectId));
        for (OperationLog entry : logs) {
            items.add(new TimelineItem(entry.getCreatedAt(), entry.getOperatorName(), entry.getAction(),
                    entry.getDetail(), "OPERATION"));
        }
        // state-kit 流转历史：machine 名 = 对象类型小写（task/issue/draft）
        List<HistoryEntry> history = historyQueryService.query(objectType.toLowerCase(), objectId);
        for (HistoryEntry h : history) {
            items.add(new TimelineItem(h.getCreateTime(), operatorDisplay(h.getOperatorId()),
                    h.getEvent(), "状态流转：" + h.getFromState() + " → " + h.getToState(), "STATE_MACHINE"));
        }
        items.sort(Comparator.comparing(TimelineItem::time).reversed());
        return items;
    }

    /** state-kit 历史只有操作人 id，展示名按 ID 前缀展示即可（与操作日志互补）。 */
    private String operatorDisplay(String operatorId) {
        return operatorId == null || operatorId.isBlank() ? "system" : "用户#" + operatorId;
    }

    /** 时间线条目。 */
    public record TimelineItem(LocalDateTime time, String operator, String action, String detail, String source) {
    }
}

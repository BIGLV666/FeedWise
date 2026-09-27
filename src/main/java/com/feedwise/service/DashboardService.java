package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedwise.mapper.CandidateIssueMapper;
import com.feedwise.mapper.FeedbackMapper;
import com.feedwise.mapper.ImprovementTaskMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作台统计（唯一扩展功能）：各对象状态/模块分布。最近动态由 /api/history 承担。 */
@Service
public class DashboardService {

    private final FeedbackMapper feedbackMapper;
    private final CandidateIssueMapper issueMapper;
    private final ImprovementTaskMapper taskMapper;

    public DashboardService(FeedbackMapper feedbackMapper, CandidateIssueMapper issueMapper,
                            ImprovementTaskMapper taskMapper) {
        this.feedbackMapper = feedbackMapper;
        this.issueMapper = issueMapper;
        this.taskMapper = taskMapper;
    }

    /** @return 工作台统计快照（反馈状态/模块分布、候选问题与任务状态分布） */
    public Snapshot snapshot() {
        return new Snapshot(
                countBy(feedbackMapper, "status"),
                countBy(feedbackMapper, "module"),
                countBy(issueMapper, "status"),
                countBy(taskMapper, "status"));
    }

    /** 按列分组计数（通用 GROUP BY）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Long> countBy(BaseMapper<?> mapper, String column) {
        Map<String, Long> result = new LinkedHashMap<>();
        QueryWrapper<Object> qw = new QueryWrapper<>()
                .select(column + " AS k, COUNT(*) AS c")
                .groupBy(column);
        List<Map<String, Object>> rows = ((BaseMapper<Object>) mapper).selectMaps(qw);
        for (Map<String, Object> row : rows) {
            Object key = row.get("k");
            Object count = row.get("c");
            result.put(key == null ? "NULL" : key.toString(),
                    count instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(count)));
        }
        return result;
    }

    /** 统计快照。 */
    public record Snapshot(Map<String, Long> feedbackByStatus, Map<String, Long> feedbackByModule,
                           Map<String, Long> issueByStatus, Map<String, Long> taskByStatus) {
    }
}

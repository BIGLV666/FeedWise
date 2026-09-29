package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedwise.entity.DashboardSnapshot;
import com.feedwise.mapper.CandidateIssueMapper;
import com.feedwise.mapper.FeedbackMapper;
import com.feedwise.mapper.ImprovementTaskMapper;
import io.github.biglv666.cachekit.annotation.CacheHandle;
import io.github.biglv666.cachekit.core.EntityCache;
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

    /**
     * 工作台统计（@CacheHandle 手动句柄演示：30 秒 TTL 内统计零重复计算，
     * 与 @CachedQuery 注解式缓存对照；新数据最多 30 秒延迟，工作台场景可接受）。
     */
    @CacheHandle(DashboardSnapshot.class)
    private EntityCache<DashboardSnapshot> snapshotCache;

    /** @return 工作台统计快照（反馈状态/模块分布、候选问题与任务状态分布） */
    public DashboardSnapshot snapshot() {
        DashboardSnapshot cached = snapshotCache.get("global", this::computeSnapshot);
        return cached != null ? cached : computeSnapshot();
    }

    private DashboardSnapshot computeSnapshot() {
        return new DashboardSnapshot("global",
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
}
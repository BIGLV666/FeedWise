package com.feedwise.controller;

import io.github.biglv666.authkit.annotation.RequireLogin;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全家桶能力地图：8 个组件的用途、在 demo 中的使用点与演示方式，
 * 附运行期指标（Micrometer，存在即取、不存在显示 0）。"组件集合展示"主入口数据。
 *
 * <p>演示入口分两类：pageEntry 是前端页面（可路由跳转）；
 * apiEntries 是后端接口（前端在站内弹窗 fetch 展示响应，不裸跳）。</p>
 */
@RestController
@RequestMapping("/api/capability")
@RequireLogin
public class CapabilityController {

    private final MeterRegistry meterRegistry;

    public CapabilityController(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping
    public List<ComponentCard> map() {
        List<ComponentCard> cards = new ArrayList<>();
        cards.add(new ComponentCard("auth-kit", "认证鉴权",
                "四角色会话登录（不透明 token+Redis）、@RequireRole/@RequirePermission 两级模型、@RequireSafe 二级认证（合并敏感操作）、记住我、同端顶号、在线会话与踢人",
                "/sessions", List.of(), Map.of()));
        cards.add(new ComponentCard("web-common", "统一返回体与异常",
                "Result 自动包装、全局异常、业务错误码分段（40100/40300/40900/40906/50301）、@ErrorCodeScan 字典端点、@NoWrap（CSV 导出）、@DefaultErrorCode（AI 异常映射）",
                null, List.of(api("错误码字典（组件端点）", "/web-common/error-codes")), Map.of()));
        cards.add(new ComponentCard("api-governance", "API 治理",
                "@RateLimit（登录/AI，0.6.0 标准响应头）、慢调用日志、@AsyncAction/@AsyncHandler 异步钩子（AI 整理四阶段）、告警 webhook+恢复通知（→操作时间线）、管理端点、@NoLog",
                null, List.of(
                        api("治理请求指标（Micrometer）", "/actuator/metrics/api.governance.requests"),
                        api("异步线程池水位", "/actuator/metrics/api.governance.async.pool.active"),
                        api("管理端点（需静态令牌 X-Auth-Token: feedwise-gov）", "/api-governance/management")),
                Map.of("异步钩子执行", count("api.governance.async.executions"))));
        cards.add(new ComponentCard("concurrent-guard", "并发防护",
                "@Idempotent 双模式：REJECT（导入/AI，40906）与 REPLAY（转任务，重放上次 taskId）+ 完成哨兵宽限；@DistributedLock 与 LockTemplate 编程式锁；GuardRejectedEvent 留痕（历史页搜索 GUARD_REJECTED）",
                "/history", List.of(), Map.of("幂等拒绝", count("guard_rejected_total"))));
        cards.add(new ComponentCard("state-kit", "声明式状态机",
                "task/issue/draft 三台 yml 状态机、CAS 唯一写入口、StateGuard（验证结果必填）/StateAction（PASS 联动）、StateTransitedEvent 时间线、availableActions 可操作视图、Mermaid 可视化导出、冲突自动重试",
                "/machines", List.of(api("状态机声明导出（Mermaid/DOT）", "/api/machines/diagrams")), Map.of()));
        cards.add(new ComponentCard("cache-kit", "三级缓存",
                "BaseMapper 六方法自动缓存、@CachedQuery（用户查询）、@CacheEntity/@CacheHandle（工作台快照 30s）、binlog 直连失效（绕过 MP 的写，GTID 位点）、withDb 强一致读（合并目标校验）",
                "/dashboard", List.of(
                        api("L1 缓存请求指标", "/actuator/metrics/cache-kit.l1.requests"),
                        api("DB 回源次数指标", "/actuator/metrics/cache-kit.db.loads")),
                Map.of("L1 命中", count("cache-kit.l1.requests", "result", "hit"),
                        "L2 命中", count("cache-kit.l2.requests", "result", "hit"))));
        cards.add(new ComponentCard("data-scope", "行级数据权限",
                "@DataScope（反馈按 created_by、任务按 assignee_id 自动改写 SQL）、ScopeResolver 决策（SUPPORT→self、SUPPORT_LEAD→deptIn 本组、PM/DEV→all）、fail-closed；用 lead1 登录可对比范围差异",
                "/feedbacks", List.of(), Map.of()));
        cards.add(new ComponentCard("OutboxPro", "事务消息",
                "批量导入事务 outbox→RabbitMQ→AI 消费者（RELIABLE）、注解式事件（@OutboxEvent/@OutboxHandler，BEST_EFFORT 通知）、Inbox 幂等、@NonRetryable（模型不可用直进死信）、死信台账+人工重放、ops 端点",
                "/dlq", List.of(api("死信台账（业务代理接口）", "/api/dlq")), Map.of()));
        return cards;
    }

    private long count(String meterName) {
        return count(meterName, null, null);
    }

    /** 兼容 Map.of 的两参调用点。 */
    private long count(String meterName, String tagKey) {
        return count(meterName, tagKey, null);
    }

    /** 按指标名+tag 读计数器；无注册表或指标不存在返回 0。 */
    private long count(String meterName, String tagKey, String tagValue) {
        if (meterRegistry == null) {
            return 0;
        }
        try {
            double value;
            if (tagKey == null) {
                value = meterRegistry.find(meterName).counters().stream()
                        .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
            } else {
                value = meterRegistry.find(meterName).tag(tagKey, tagValue).counters().stream()
                        .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
            }
            return (long) value;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 组件卡片。
     *
     * @param name       组件名
     * @param role       定位
     * @param usage      本项目中的使用点
     * @param pageEntry  前端页面入口（可路由跳转，无则 null）
     * @param apiEntries 后端接口清单（前端站内弹窗实时拉取展示）
     * @param metrics    运行指标
     */
    public record ComponentCard(String name, String role, String usage, String pageEntry,
                                List<ApiEntry> apiEntries, Map<String, Long> metrics) {

        public ComponentCard {
            apiEntries = apiEntries == null ? List.of() : List.copyOf(apiEntries);
            metrics = metrics == null ? Map.of() : new LinkedHashMap<>(metrics);
        }
    }

    /**
     * 后端接口入口。
     *
     * @param label 展示名
     * @param path  接口路径（站内相对路径，前端 fetch 展示响应）
     */
    public record ApiEntry(String label, String path) {
    }

    private static ApiEntry api(String label, String path) {
        return new ApiEntry(label, path);
    }
}
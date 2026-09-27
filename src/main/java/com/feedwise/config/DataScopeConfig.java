package com.feedwise.config;

import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.model.ScopePolicy;
import io.github.biglv666.datascope.spi.ScopeResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * data-scope 行级权限决策（业务唯一必配 Bean）。
 *
 * <p>决策规则（组件不理解角色语义，归属判断全部在这里）：</p>
 * <ul>
 *   <li>SUPPORT（客服）：只看自己录入的反馈（feedback.created_by）、指派给自己的任务；</li>
 *   <li>DEV（开发/测试）：只看指派给自己的任务（improvement_task.assignee_id）；</li>
 *   <li>PM 及未知角色：全量（PM 需要横向对比所有客服录入的反馈）。</li>
 * </ul>
 *
 * <p>fail-closed：决策异常/未登录一律拒绝执行，绝不静默放行全量。</p>
 */
@Configuration
public class DataScopeConfig {

    @Bean
    public ScopeResolver scopeResolver() {
        return (ScopeContext ctx) -> {
            if (ctx.hasRole("PM")) {
                return ScopePolicy.all();
            }
            // SUPPORT 与 DEV 均收敛到本人数据：反馈按 created_by、任务按 assignee_id 过滤
            return ScopePolicy.self(Long.parseLong(ctx.getUserId()));
        };
    }
}

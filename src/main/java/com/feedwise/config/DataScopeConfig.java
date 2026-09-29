package com.feedwise.config;

import com.feedwise.entity.User;
import com.feedwise.mapper.UserMapper;
import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.model.ScopePolicy;
import io.github.biglv666.datascope.spi.ScopeResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * data-scope 行级权限决策（业务唯一必配 Bean）。
 *
 * <p>决策规则（组件不理解角色语义，归属判断全部在这里）：</p>
 * <ul>
 *   <li>SUPPORT（客服）：只看自己录入的反馈（feedback.created_by）、指派给自己的任务；</li>
 *   <li>SUPPORT_LEAD（客服主管）：看本组（dept 同组客服录入的反馈，deptIn 演示）；</li>
 *   <li>DEV（开发/测试）：只看指派给自己的任务（improvement_task.assignee_id）；</li>
 *   <li>PM 及未知角色：全量（PM 需要横向对比所有客服录入的反馈）。</li>
 * </ul>
 *
 * <p>fail-closed：决策异常/未登录一律拒绝执行，绝不静默放行全量。
 * 注意：UserMapper 经 ObjectProvider 运行时惰性获取——resolver Bean 若直接依赖 Mapper，
 * 会与 data-scope 拦截器构成 SqlSessionFactory 启动循环。</p>
 */
@Configuration
public class DataScopeConfig {

    @Bean
    public ScopeResolver scopeResolver(ObjectProvider<UserMapper> userMapperProvider) {
        return (ScopeContext ctx) -> {
            if (ctx.hasRole("PM") || ctx.hasRole("DEV")) {
                return ScopePolicy.all();
            }
            long userId = Long.parseLong(ctx.getUserId());
            if (ctx.hasRole("SUPPORT_LEAD")) {
                UserMapper userMapper = userMapperProvider.getObject();
                // 主管看本组：同 dept 的客服 id 集合（反馈组 → 小王等）
                User lead = userMapper.getUserById(userId);
                if (lead == null || lead.getDept() == null) {
                    return ScopePolicy.self(userId);
                }
                List<Long> memberIds = userMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                        .eq(User::getDept, lead.getDept())
                                        .eq(User::getRole, "SUPPORT"))
                        .stream().map(User::getId).toList();
                if (memberIds.isEmpty()) {
                    return ScopePolicy.self(userId);
                }
                return ScopePolicy.deptInLongs(memberIds);
            }
            // SUPPORT 收敛到本人数据：反馈按 created_by、任务按 assignee_id 过滤
            return ScopePolicy.self(userId);
        };
    }
}

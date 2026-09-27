package com.feedwise;

import com.feedwise.entity.User;
import com.feedwise.mapper.UserMapper;
import com.feedwise.support.TestBase;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cache-kit 冒烟测试（需本机 Redis，无则跳过）：getUserById 命中三级缓存，
 * 直写 DB 后通过 updateById 失效缓存读到新值。
 */
class CacheKitSmokeTest extends TestBase {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class,
                    org.apache.ibatis.session.ResultHandler.class}))
    static class QueryCounter implements Interceptor {
        final Map<String, Integer> counts = new HashMap<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            counts.merge(ms.getId(), 1, Integer::sum);
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }
    }

    @Test
    void cached_query_hits_cache_and_invalidates_on_update() {
        org.junit.jupiter.api.Assumptions.assumeTrue(() -> {
            try (var socket = new java.net.Socket("localhost", 6379)) {
                return true;
            } catch (Exception e) {
                return false;
            }
        }, "本机无 Redis，跳过 cache-kit 冒烟测试");

        QueryCounter counter = new QueryCounter();
        sqlSessionFactory.getConfiguration().addInterceptor(counter);

        // 首次查询：可能回源（此时已注册计数器，至少后续不再回源）
        User first = userMapper.getUserById(4L);
        assertThat(first).isNotNull();

        int before = counter.counts.getOrDefault("com.feedwise.mapper.UserMapper.getUserById", 0);

        // 再次查询：应命中缓存，DB 查询计数不变
        User second = userMapper.getUserById(4L);
        assertThat(second.getDisplayName()).isEqualTo(first.getDisplayName());
        int after = counter.counts.getOrDefault("com.feedwise.mapper.UserMapper.getUserById", 0);
        assertThat(after).isEqualTo(before);

        // 直写 DB（绕过应用，模拟脏数据）→ 缓存仍是旧值，证明缓存生效
        jdbcTemplate.update("UPDATE fw_user SET display_name = '张工改' WHERE id = 4");
        User cached = userMapper.getUserById(4L);
        assertThat(cached.getDisplayName()).isEqualTo(first.getDisplayName());

        // updateById 走 cache-kit 失效（含直写场景由再次 updateById 兜底）→ 读到 DB 新值
        User patch = new User();
        patch.setId(4L);
        patch.setDisplayName("张工");
        userMapper.updateById(patch);
        User refreshed = userMapper.getUserById(4L);
        assertThat(refreshed.getDisplayName()).isEqualTo("张工");
    }
}

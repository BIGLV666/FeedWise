package com.feedwise.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RedissonClient 声明：guard 分布式锁（@DistributedLock）与编程式锁（LockTemplate）
 * 的 Redis 锁实现依赖它；单节点地址复用 spring.data.redis 配置（Redisson 依赖为 optional 引入）。
 */
@Configuration
public class RedisLockConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host:localhost}") String host,
                                         @Value("${spring.data.redis.port:6379}") int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}

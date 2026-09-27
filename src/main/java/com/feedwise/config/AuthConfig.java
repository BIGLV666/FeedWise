package com.feedwise.config;

import com.feedwise.mapper.UserMapper;
import io.github.biglv666.authkit.spi.PermissionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * auth-kit SPI 配置。auth-kit 不带用户表：角色经 PermissionProvider 实时查 fw_user。
 * 角色：SUPPORT（客服）/ PM（产品经理）/ DEV（开发测试）。
 */
@Configuration
public class AuthConfig {

    /**
     * 权限数据源：本项目只用角色模型，权限位恒为空集。
     *
     * <p>auth-kit 每次鉴权都会调用（不缓存），变更实时生效；用户表很小，
     * 且走 cache-kit 三级缓存，热路径开销可忽略。</p>
     */
    @Bean
    public PermissionProvider permissionProvider(UserMapper userMapper) {
        return new PermissionProvider() {
            @Override
            public Set<String> getPermissions(String userId) {
                return Set.of();
            }

            @Override
            public Set<String> getRoles(String userId) {
                try {
                    long id = Long.parseLong(userId);
                    com.feedwise.entity.User user = userMapper.getUserById(id);
                    return user == null ? Set.of() : Set.of(user.getRole());
                } catch (NumberFormatException e) {
                    return Set.of();
                }
            }
        };
    }
}

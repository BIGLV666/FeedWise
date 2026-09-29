package com.feedwise.config;

import com.feedwise.mapper.UserMapper;
import io.github.biglv666.authkit.spi.PermissionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * auth-kit SPI 配置。auth-kit 不带用户表：角色与权限位经 PermissionProvider 实时查 fw_user。
 * 角色：SUPPORT（客服）/ SUPPORT_LEAD（客服主管）/ PM（产品经理）/ DEV（开发测试）。
 *
 * <p>权限位按角色推导（演示 RBAC 两级模型）：PM 拥有全部决策类权限码，
 * SUPPORT_LEAD 拥有导出权限；接口按 @RequirePermission 声明，与 @RequireRole 并存展示。</p>
 */
@Configuration
public class AuthConfig {

    /** PM 决策类权限码（与前端演示入口一一对应） */
    public static final Set<String> PM_PERMISSIONS = Set.of(
            "issue:confirm", "issue:merge", "draft:convert", "feedback:export", "dlq:replay");
    /** 客服主管权限码 */
    public static final Set<String> LEAD_PERMISSIONS = Set.of("feedback:export");

    @Bean
    public PermissionProvider permissionProvider(UserMapper userMapper) {
        return new PermissionProvider() {
            @Override
            public Set<String> getPermissions(String userId) {
                try {
                    long id = Long.parseLong(userId);
                    com.feedwise.entity.User user = userMapper.getUserById(id);
                    if (user == null) {
                        return Set.of();
                    }
                    return switch (user.getRole()) {
                        case "PM" -> PM_PERMISSIONS;
                        case "SUPPORT_LEAD" -> LEAD_PERMISSIONS;
                        default -> Set.of();
                    };
                } catch (NumberFormatException e) {
                    return Set.of();
                }
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

package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.User;
import com.feedwise.mapper.UserMapper;
import io.github.biglv666.authkit.AuthKit;
import io.github.biglv666.authkit.model.DeviceType;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 登录/登出/当前用户/二级认证。密码校验由业务完成后调用 AuthKit 签发会话 token。 */
@Service
public class AuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 登录：校验用户名密码，签发不透明 token（Redis 会话，同端顶号）。
     * rememberMe=true 走长效期会话（7 天免登录演示）。
     *
     * @param username    登录名
     * @param rawPassword 明文密码
     * @param rememberMe  是否记住我
     * @return token 与用户信息
     * @throws BusinessException 用户不存在或密码错误（不区分，防枚举）
     */
    public LoginResult login(String username, String rawPassword, boolean rememberMe) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !ENCODER.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(FeedWiseErrorCode.BAD_CREDENTIALS);
        }
        String token = AuthKit.login(user.getId(), DeviceType.PC, rememberMe);
        return new LoginResult(token, new UserInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
    }

    /** 登出当前会话。 */
    public void logout() {
        AuthKit.logout();
    }

    /**
     * 二级认证：校验密码成功后开启安全态（敏感操作 N 分钟免二次验密）。
     * 用于"合并候选问题"这类高风险操作。
     */
    public void openSafe(String username, String rawPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !ENCODER.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(FeedWiseErrorCode.BAD_CREDENTIALS);
        }
        AuthKit.openSafe();
    }

    /** @return 当前是否在二级认证有效期内 */
    public boolean isSafe() {
        return AuthKit.isSafe();
    }

    /** 关闭二级认证安全态（敏感操作完成后主动失效）。 */
    public void closeSafe() {
        AuthKit.closeSafe();
    }

    /** 登录结果。 */
    public record LoginResult(String token, UserInfo user) {
    }

    /** 用户信息（不含密码）。 */
    public record UserInfo(Long id, String username, String displayName, String role) {
    }
}

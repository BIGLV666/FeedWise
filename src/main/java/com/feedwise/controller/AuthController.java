package com.feedwise.controller;

import com.feedwise.dto.LoginRequest;
import com.feedwise.service.AuthService;
import io.github.biglv666.apigovernance.annotation.NoLog;
import io.github.biglv666.apigovernance.annotation.RateLimit;
import io.github.biglv666.authkit.annotation.CurrentUser;
import io.github.biglv666.authkit.model.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录 / 登出 / 当前用户 / 二级认证。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录（白名单端点）。按用户名维度限流；rememberMe=true 走 7 天长效会话。
     */
    @PostMapping("/login")
    @RateLimit(limit = 10, window = 60, key = "#request.username")
    public AuthService.LoginResult login(@RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password(), Boolean.TRUE.equals(request.rememberMe()));
    }

    /** 登出当前会话。 */
    @PostMapping("/logout")
    public void logout() {
        authService.logout();
    }

    /** 二级认证开启：敏感操作（合并候选问题）前前端弹密码框调用。 */
    @PostMapping("/safe")
    public void openSafe(@RequestBody LoginRequest request) {
        authService.openSafe(request.username(), request.password());
    }

    /** 查询当前二级认证状态。 */
    @GetMapping("/safe")
    public boolean isSafe() {
        return authService.isSafe();
    }

    /** 关闭二级认证安全态。 */
    @PostMapping("/safe/close")
    public void closeSafe() {
        authService.closeSafe();
    }

    /** @return 当前登录用户信息（高频轮询接口，关闭治理日志防刷屏） */
    @GetMapping("/me")
    @NoLog
    public AuthService.UserInfo me(@CurrentUser AuthUser user) {
        return new AuthService.UserInfo(user.getUserIdAsLong(), null, null, com.feedwise.common.CurrentUser.role());
    }
}

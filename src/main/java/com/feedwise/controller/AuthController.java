package com.feedwise.controller;

import com.feedwise.dto.LoginRequest;
import com.feedwise.service.AuthService;
import io.github.biglv666.apigovernance.annotation.RateLimit;
import io.github.biglv666.authkit.AuthKit;
import io.github.biglv666.authkit.model.AuthUser;
import io.github.biglv666.authkit.annotation.CurrentUser;
import io.github.biglv666.webcommon.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录 / 登出 / 当前用户。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录（白名单端点）。按用户名维度限流，防爆破由 auth-kit 登录防爆破兜底。
     *
     * @param request 用户名密码
     * @return token 与用户信息
     */
    @PostMapping("/login")
    @RateLimit(limit = 10, window = 60, key = "#request.username")
    public AuthService.LoginResult login(@RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    /** 登出当前会话。 */
    @PostMapping("/logout")
    public void logout() {
        authService.logout();
    }

    /** @return 当前登录用户信息 */
    @GetMapping("/me")
    public AuthService.UserInfo me(@CurrentUser AuthUser user) {
        return new AuthService.UserInfo(user.getUserIdAsLong(), null, null, com.feedwise.common.CurrentUser.role());
    }
}

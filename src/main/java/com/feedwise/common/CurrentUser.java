package com.feedwise.common;

import io.github.biglv666.authkit.AuthKit;

/** 当前登录用户便捷取值（id/角色均来自 auth-kit 登录态，绝不信任前端提交）。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** @return 当前登录用户 id */
    public static long id() {
        return AuthKit.getLoginIdAsLong();
    }

    /** @return 当前用户角色编码：PM / SUPPORT / DEV（无角色为 UNKNOWN） */
    public static String role() {
        for (String role : new String[]{"PM", "SUPPORT", "DEV"}) {
            if (AuthKit.hasRole(role)) {
                return role;
            }
        }
        return "UNKNOWN";
    }
}

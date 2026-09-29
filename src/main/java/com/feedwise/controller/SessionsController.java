package com.feedwise.controller;

import com.feedwise.entity.User;
import com.feedwise.mapper.UserMapper;
import io.github.biglv666.authkit.AuthKit;
import io.github.biglv666.authkit.annotation.RequireRole;
import io.github.biglv666.authkit.model.AuthSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 在线会话管理（PM 专属）：遍历用户表列出在线会话，支持强制下线。
 * 会话数据来自 auth-kit Redis 会话存储（踢人下线后旧 token 收到 KICKED_OUT 语义）。
 */
@RestController
@RequestMapping("/api/sessions")
@RequireRole("PM")
public class SessionsController {

    private final UserMapper userMapper;

    public SessionsController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 在线会话列表（按用户聚合）。
     */
    @GetMapping
    public List<SessionItem> list() {
        List<SessionItem> items = new ArrayList<>();
        List<User> users = userMapper.selectList(null);
        for (User user : users) {
            List<AuthSession> sessions = AuthKit.listSessions(user.getId(), null);
            for (AuthSession session : sessions) {
                items.add(new SessionItem(user.getId(), user.getDisplayName(), user.getRole(),
                        session.getDevice(), session.getLoginTime(), session.getLastActiveTime(),
                        session.isRememberMe(), session.getToken()));
            }
        }
        items.sort(Comparator.comparingLong(SessionItem::lastActiveTime).reversed());
        return items;
    }

    /**
     * 强制下线（踢人）：指定用户的全部设备下线。
     *
     * @param userId 用户 id
     */
    @DeleteMapping("/{userId}")
    public void kickoutUser(@PathVariable Long userId) {
        AuthKit.kickout(userId, (String) null);
    }

    /** 会话条目（token 截断展示，避免完整凭证落前端日志）。 */
    public record SessionItem(Long userId, String displayName, String role, String device,
                              long loginTime, long lastActiveTime, boolean rememberMe, String tokenMasked) {

        public SessionItem {
            tokenMasked = tokenMasked == null || tokenMasked.length() < 12
                    ? "***" : tokenMasked.substring(0, 8) + "…";
        }
    }
}

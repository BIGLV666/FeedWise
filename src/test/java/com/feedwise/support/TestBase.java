package com.feedwise.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试基类：H2 + mock AI + OutboxPro 关闭（见 application-test.yml）。
 * 登录走真实 auth-kit 流程（无 Redis 时自动降级内存会话）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每个测试方法前清理 guard 幂等键，避免上一次运行的 TTL 键跨运行污染（无 Redis 时静默跳过）。
     */
    @org.junit.jupiter.api.BeforeEach
    void cleanupGuardKeys() {
        try {
            if (stringRedisTemplate == null) {
                return;
            }
            var keys = stringRedisTemplate.keys("guard:idempotent:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
            // 无 Redis 环境（幂等 fail-open）直接跳过
        }
    }

    /**
     * 用演示账号登录并返回 token。
     *
     * @param username 账号（support1/support2/pm/dev1，密码统一 123456）
     * @return 不透明 token
     */
    protected String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        if (body.path("code").asInt() != 0) {
            throw new IllegalStateException("登录失败: " + body);
        }
        return body.path("data").path("token").asText();
    }

    /**
     * 从统一 Result 响应中取 code。
     */
    protected int code(MvcResult result) throws Exception {
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(raw);
        int c = body.path("code").asInt();
        if (c != 0) {
            Exception ex = result.getResolvedException();
            System.err.println("[TEST-DEBUG] non-zero code=" + c + " body=" + raw
                    + " resolved=" + (ex == null ? "null" : ex.getClass().getName() + ": " + ex.getMessage()));
        }
        return c;
    }
}

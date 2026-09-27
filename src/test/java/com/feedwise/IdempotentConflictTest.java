package com.feedwise;

import com.feedwise.support.TestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 幂等与冲突反馈测试：重复导入被 @Idempotent 拒绝（40906），冲突形态可见。
 * 注意：guard 幂等策略依赖 Redis（本机 cache-kit-redis:6379）；无 Redis 时组件 fail-open，
 * 本测试通过 socket 探测自动跳过。
 */
class IdempotentConflictTest extends TestBase {

    private static boolean redisAvailable() {
        try (var socket = new java.net.Socket("localhost", 6379)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void duplicate_import_is_rejected_with_40906() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable(), "本机无 Redis，跳过幂等测试");
        String support = login("support2");
        String body = "{\"items\":[{\"content\":\"幂等测试反馈-只应入库一次\"}]}";
        MvcResult first = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/feedbacks/import")
                        .header("Authorization", "Bearer " + support)
                        .contentType("application/json").content(body))
                .andReturn();
        assertThat(code(first)).isZero();

        // TTL 内的重复请求：guard REJECT → 统一 Result 40906
        MvcResult second = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/feedbacks/import")
                        .header("Authorization", "Bearer " + support)
                        .contentType("application/json").content(body))
                .andReturn();
        assertThat(code(second)).isEqualTo(40906);
    }
}

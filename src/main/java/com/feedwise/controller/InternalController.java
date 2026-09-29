package com.feedwise.controller;

import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.service.OperationLogService;
import io.github.biglv666.apigovernance.alert.GovernanceAlertEvent;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部回调端点：接收 api-governance 告警 webhook（慢调用/限流告警与 0.6.0 恢复通知），
 * 校验静态令牌后写入操作时间线（治理告警全程留痕）。
 */
@RestController
@RequestMapping("/api/internal")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final OperationLogService operationLogService;
    private final String expectedToken;

    public InternalController(OperationLogService operationLogService,
                              @Value("${GOV_ALERT_TOKEN:feedwise-alert}") String expectedToken) {
        this.operationLogService = operationLogService;
        this.expectedToken = expectedToken;
    }

    /**
     * api-governance 告警 webhook 接收端点。
     *
     * @param token 静态令牌（X-Alert-Token 头，未配置或错误一律拒绝）
     * @param event 告警事件（type/apiKey/path/message/elapsedMs/recovered）
     */
    @PostMapping("/governance-alert")
    public void onGovernanceAlert(@RequestHeader(value = "X-Alert-Token", required = false) String token,
                                  @RequestBody GovernanceAlertEvent event) {
        if (token == null || !token.equals(expectedToken)) {
            throw new BusinessException(FeedWiseErrorCode.BAD_CREDENTIALS, "告警令牌无效");
        }
        log.info("[gov-alert] type={} api={} elapsed={}ms recovered={}", event.getType(), event.getApiKey(),
                event.getElapsedMs(), event.isRecovered());
        operationLogService.log("AI_RUN", null,
                event.isRecovered() ? "GOV_ALERT_RECOVERED" : "GOV_ALERT", null, "api-governance",
                (event.isRecovered() ? "【恢复】 " : "") + event.getType() + "：" + event.getMessage()
                        + "（" + event.getApiKey() + "）");
    }
}

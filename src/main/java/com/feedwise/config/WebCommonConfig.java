package com.feedwise.config;

import io.github.biglv666.webcommon.annotation.EnableErrorCodeEndpoint;
import io.github.biglv666.webcommon.annotation.ErrorCodeScan;
import org.springframework.context.annotation.Configuration;

/**
 * web-common 配置：注册业务错误码分段 + 开启错误码字典端点。
 * 统一 Result 包装、全局异常处理由 web-common 自动装配，接入即生效。
 */
@Configuration
@ErrorCodeScan(basePackages = "com.feedwise.common.error")
@EnableErrorCodeEndpoint
public class WebCommonConfig {
}

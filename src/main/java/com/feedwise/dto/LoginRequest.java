package com.feedwise.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录入参。rememberMe=true 走长效会话（7 天免登录）。 */
public record LoginRequest(@NotBlank String username, @NotBlank String password, Boolean rememberMe) {
}

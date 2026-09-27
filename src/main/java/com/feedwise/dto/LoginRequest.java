package com.feedwise.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录入参。 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}

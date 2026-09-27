package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户实体（auth-kit 不带用户表，经 PermissionProvider SPI 对接本表）。 */
@Data
@TableName("fw_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，永不返回给前端 */
    private String password;

    private String displayName;

    /** SUPPORT / PM / DEV */
    private String role;

    private LocalDateTime createdAt;
}

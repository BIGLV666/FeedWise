package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户反馈（脱敏原文）。原始数据：任何角色（含 AI）都不可修改、不可删除。 */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 脱敏反馈原文 */
    private String content;

    /** 来源渠道，见 {@link com.feedwise.enums.FeedbackSource} */
    private String source;

    /** 功能模块，见 {@link com.feedwise.enums.FeedbackModule} */
    private String module;

    /** 客户标签（脱敏） */
    private String customerTag;

    /** UNPROCESSED / PROCESSED */
    private String status;

    /** 录入客服 id（data-scope 行级归属列） */
    private Long createdBy;

    /** 录入客服姓名（列表展示用，非表字段） */
    @TableField(exist = false)
    private String createdByName;

    private LocalDateTime createdAt;
}

package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作时间线：关键操作留下时间、操作人和处理说明。 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** FEEDBACK / ISSUE / DRAFT / TASK / AI_RUN */
    private String objectType;

    private Long objectId;

    /** 动作编码，如 ISSUE_CONFIRMED / TASK_STATE_CHANGED */
    private String action;

    /** 操作人 id，AI 操作为 null */
    private Long operatorId;

    /** 操作人展示名（AI 形如 "AI(mock)"） */
    private String operatorName;

    /** 处理说明 */
    private String detail;

    private LocalDateTime createdAt;
}

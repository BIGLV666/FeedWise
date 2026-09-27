package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 候选卡片-原文关联：每张候选卡片必须能回到原始反馈。 */
@Data
@TableName("issue_feedback_link")
public class IssueFeedbackLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private Long feedbackId;

    private LocalDateTime createdAt;
}

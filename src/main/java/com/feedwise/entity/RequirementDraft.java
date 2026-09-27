package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 需求草稿：AI 生成的只是草稿，必须 PM 确认后才能转为改进任务。 */
@Data
@TableName("requirement_draft")
public class RequirementDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private String title;

    /** 背景说明 */
    private String background;

    /** 需求说明 */
    private String description;

    /** 验收条件 JSON 字符串数组 */
    private String acceptance;

    private String status;

    private Integer aiGenerated;

    private Long createdBy;

    private Long confirmedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

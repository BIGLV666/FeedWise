package com.feedwise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 功能改进任务：状态由 state-kit task 状态机 CAS 唯一写入口推进。 */
@Data
@TableName("improvement_task")
public class ImprovementTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long draftId;

    private Long issueId;

    private String title;

    private String detail;

    /** 指派给开发/测试人员 */
    private Long assigneeId;

    /** 指派对象姓名（列表展示用，非表字段） */
    @TableField(exist = false)
    private String assigneeName;

    /** P1/P2/P3，仅 PM 可设定 */
    private String priority;

    private String status;

    /** 验证结果（PASS/REJECT 时填写） */
    private String verifyResult;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

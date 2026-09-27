SET NAMES utf8mb4;

-- FeedWise 建表脚本（MySQL 8.4）
-- 由 docker-compose 挂载到 /docker-entrypoint-initdb.d 首次启动自动执行；
-- OutboxPro 的 outbox 表与 state-kit 的 sk_transition_history 由组件启动时自动建。

CREATE TABLE IF NOT EXISTS fw_user (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    username     VARCHAR(50)  NOT NULL COMMENT '登录名',
    password     VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    display_name VARCHAR(50)  NOT NULL COMMENT '姓名',
    role         VARCHAR(20)  NOT NULL COMMENT 'SUPPORT/PM/DEV',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fw_user_username (username)
) ENGINE = InnoDB COMMENT '用户（auth-kit SPI 对接）';

CREATE TABLE IF NOT EXISTS feedback (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    content      VARCHAR(1000) NOT NULL COMMENT '脱敏反馈原文（只增不改不删）',
    source       VARCHAR(20)   NOT NULL COMMENT 'TICKET/SURVEY/CALL',
    module       VARCHAR(30)   NOT NULL COMMENT 'REIMBURSE_FORM/INVOICE_UPLOAD/APPROVAL/RETURN_MODIFY/OTHER',
    customer_tag VARCHAR(50)   NULL COMMENT '客户标签（脱敏）',
    status       VARCHAR(20)   NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'UNPROCESSED/PROCESSED',
    created_by   BIGINT        NOT NULL COMMENT '录入客服 id',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_feedback_created_by (created_by),
    KEY idx_feedback_status (status),
    KEY idx_feedback_module (module)
) ENGINE = InnoDB COMMENT '用户反馈（原始数据，AI 不可修改）';

CREATE TABLE IF NOT EXISTS candidate_issue (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    title         VARCHAR(100) NOT NULL COMMENT '问题标题',
    module        VARCHAR(30)  NOT NULL COMMENT '涉及功能模块',
    problem       VARCHAR(500) NOT NULL COMMENT '问题现象归纳',
    demand        VARCHAR(500) NULL COMMENT '用户诉求归纳',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW/CONFIRMED/REJECTED/MERGED',
    merged_into_id BIGINT      NULL COMMENT '合并入的目标卡片 id（MERGED 时）',
    ai_generated  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=AI 生成待确认',
    similar_note  VARCHAR(200) NULL COMMENT 'AI 给 PM 的疑似相似提示',
    created_by    BIGINT       NULL COMMENT '创建人（AI 为 NULL）',
    confirmed_by  BIGINT       NULL COMMENT '确认/驳回/合并操作人',
    confirm_note  VARCHAR(200) NULL COMMENT '处理说明',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_issue_status (status)
) ENGINE = InnoDB COMMENT '候选问题卡片';

CREATE TABLE IF NOT EXISTS issue_feedback_link (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id    BIGINT   NOT NULL,
    feedback_id BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_issue_feedback (issue_id, feedback_id),
    KEY idx_link_feedback (feedback_id)
) ENGINE = InnoDB COMMENT '候选卡片-原文关联（可回查）';

CREATE TABLE IF NOT EXISTS requirement_draft (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id     BIGINT        NOT NULL,
    title        VARCHAR(100)  NOT NULL,
    background   VARCHAR(500)  NOT NULL COMMENT '背景说明',
    description  VARCHAR(1000) NOT NULL COMMENT '需求说明',
    acceptance   TEXT          NULL COMMENT '验收条件 JSON 数组',
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/CONVERTED',
    ai_generated TINYINT       NOT NULL DEFAULT 0,
    created_by   BIGINT        NULL,
    confirmed_by BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_draft_issue (issue_id),
    KEY idx_draft_status (status)
) ENGINE = InnoDB COMMENT '需求草稿（AI 产物必须 PM 确认后才能建任务）';

CREATE TABLE IF NOT EXISTS improvement_task (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id     BIGINT       NOT NULL COMMENT '来源草稿',
    issue_id     BIGINT       NOT NULL COMMENT '来源候选问题',
    title        VARCHAR(100) NOT NULL,
    detail       VARCHAR(1000) NULL COMMENT '任务说明',
    assignee_id  BIGINT       NULL COMMENT '指派给开发/测试',
    priority     VARCHAR(10)  NOT NULL DEFAULT 'P2' COMMENT 'P1/P2/P3，仅 PM 可定',
    status       VARCHAR(20)  NOT NULL DEFAULT 'TODO' COMMENT 'TODO/IN_PROGRESS/PENDING_VERIFY/DONE（state-kit CAS 唯一写入口）',
    verify_result VARCHAR(500) NULL COMMENT '验证结果（PASS/REJECT 时填写）',
    created_by   BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_status (status),
    KEY idx_task_assignee (assignee_id)
) ENGINE = InnoDB COMMENT '功能改进任务';

CREATE TABLE IF NOT EXISTS operation_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    object_type   VARCHAR(20)  NOT NULL COMMENT 'FEEDBACK/ISSUE/DRAFT/TASK/AI_RUN',
    object_id     BIGINT       NULL,
    action        VARCHAR(40)  NOT NULL COMMENT '动作编码',
    operator_id   BIGINT       NULL COMMENT '操作人 id（AI 为 NULL）',
    operator_name VARCHAR(50)  NOT NULL COMMENT '操作人展示名（如 AI(mock)）',
    detail        VARCHAR(1000) NULL COMMENT '处理说明',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_log_object (object_type, object_id, created_at)
) ENGINE = InnoDB COMMENT '操作时间线（审计）';

-- H2(MODE=MySQL) 测试建表（与 Sql/schema.sql 等价，去掉 MySQL 方言子句）
CREATE TABLE IF NOT EXISTS fw_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    dept VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fw_user_username UNIQUE (username)
);
CREATE TABLE IF NOT EXISTS feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content VARCHAR(1000) NOT NULL,
    source VARCHAR(20) NOT NULL,
    module VARCHAR(30) NOT NULL,
    customer_tag VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'UNPROCESSED',
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS candidate_issue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    module VARCHAR(30) NOT NULL,
    problem VARCHAR(500) NOT NULL,
    demand VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    merged_into_id BIGINT,
    ai_generated TINYINT NOT NULL DEFAULT 0,
    similar_note VARCHAR(200),
    created_by BIGINT,
    confirmed_by BIGINT,
    confirm_note VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS issue_feedback_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    feedback_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_feedback UNIQUE (issue_id, feedback_id)
);
CREATE TABLE IF NOT EXISTS requirement_draft (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    background VARCHAR(500) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    acceptance TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ai_generated TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    confirmed_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS improvement_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    detail VARCHAR(1000),
    assignee_id BIGINT,
    priority VARCHAR(10) NOT NULL DEFAULT 'P2',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    verify_result VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    object_type VARCHAR(20) NOT NULL,
    object_id BIGINT,
    action VARCHAR(40) NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(50) NOT NULL,
    detail VARCHAR(1000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

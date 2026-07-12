-- V18: 创建工时记录表
CREATE TABLE time_entry (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    work_date DATE NOT NULL,
    duration INT NOT NULL,              -- 时长（分钟）
    start_time INT,                     -- 开始时间（分钟数，如 540 = 09:00）
    work_type VARCHAR(50),              -- 工作类型（Development/Testing/Documentation/Design/Review/Meeting/Other）
    description TEXT,                   -- 描述
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_time_entry_user_date ON time_entry(user_id, work_date);
CREATE INDEX idx_time_entry_issue ON time_entry(issue_id);
CREATE INDEX idx_time_entry_date_range ON time_entry(work_date);

COMMENT ON TABLE time_entry IS '工时记录表';
COMMENT ON COLUMN time_entry.duration IS '时长（分钟）';
COMMENT ON COLUMN time_entry.start_time IS '开始时间（分钟数，如 540 表示 09:00）';
COMMENT ON COLUMN time_entry.work_type IS '工作类型：Development/Testing/Documentation/Design/Review/Meeting/Other';

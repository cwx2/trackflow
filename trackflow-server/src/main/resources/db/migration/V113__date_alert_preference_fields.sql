-- V113: Date Alert 偏好字段 + issue.due_date 优化索引
-- REQ-443-1: 通知系统 Date Alert 后端支持

-- 1. notification_preference 新增日期提醒相关字段
ALTER TABLE notification_preference ADD COLUMN on_due_date BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE notification_preference ADD COLUMN on_overdue BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE notification_preference ADD COLUMN due_date_advance_days INTEGER NOT NULL DEFAULT 1;

-- 2. 优化 issue.due_date 索引为 partial index（仅索引未删除且有 due_date 的记录）
DROP INDEX IF EXISTS idx_issue_due_date;
CREATE INDEX idx_issue_due_date_active ON issue(due_date)
    WHERE deleted_at IS NULL AND due_date IS NOT NULL;

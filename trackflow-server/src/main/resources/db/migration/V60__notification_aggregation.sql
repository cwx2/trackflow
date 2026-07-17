-- V60: 通知聚合支持
-- 新增 updated_at 和 aggregation_count 字段，支持短时间内同类型通知合并

ALTER TABLE notification
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN aggregation_count INT NOT NULL DEFAULT 1;

-- 创建聚合查询索引：同一用户+同一类型+同一资源+未读 的最近通知
CREATE INDEX idx_notification_aggregation
    ON notification (user_id, type, resource_type, resource_id)
    WHERE is_read = false;

COMMENT ON COLUMN notification.updated_at IS '通知最后被聚合更新的时间';
COMMENT ON COLUMN notification.aggregation_count IS '该通知聚合了多少次操作（默认1）';

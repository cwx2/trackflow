-- 通知表新增 actor_id 字段，记录通知触发者
-- 允许 NULL（系统自动通知时无 actor）
ALTER TABLE notification ADD COLUMN actor_id BIGINT REFERENCES sys_user(id);

-- 为 actor_id 添加索引（支持按触发者查询/分组）
CREATE INDEX idx_notification_actor_id ON notification(actor_id) WHERE actor_id IS NOT NULL;

COMMENT ON COLUMN notification.actor_id IS '通知触发者用户ID，NULL表示系统自动触发';

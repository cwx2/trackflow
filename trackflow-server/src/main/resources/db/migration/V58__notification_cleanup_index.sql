-- V58: 通知清理机制 - 添加复合索引支持按 user_id + is_read + created_at 高效查询/清理
-- 现有 idx_notif_user_unread (user_id, is_read) 不包含 created_at，无法高效查询过期通知

-- 删除旧的部分索引
DROP INDEX IF EXISTS idx_notif_user_unread;

-- 创建新的复合索引（覆盖用户通知列表查询 + 过期清理查询）
CREATE INDEX idx_notif_user_read_created ON notification (user_id, is_read, created_at DESC);

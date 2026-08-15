-- V304: 为 notification 表添加聚合唯一约束，防止并发 TOCTOU 竞态产生重复通知
--
-- 背景：NotificationService.notify 采用"查询聚合窗口内的未读通知 → 有则更新/无则插入"的模式，
-- 在高并发下两个线程可能同时查到 null 并各自 INSERT，导致用户收到重复通知。
-- 部分唯一索引（WHERE is_read = false）保证同一资源只有一条活跃未读通知，
-- 配合 INSERT ... ON CONFLICT 可以原子化地实现聚合逻辑。
--
-- 注意：历史上可能存在同一 (user_id, type, resource_type, resource_id) 的多条未读通知，
-- 创建索引前先对重复数据进行归并（保留最新一条，其余标记为已读）。

-- Step 1：归并重复未读通知（保留 updated_at 最新的一条，其余标记为 is_read=true）
UPDATE notification n
SET is_read = true
WHERE is_read = false
  AND n.id NOT IN (
      SELECT DISTINCT ON (user_id, type, resource_type, resource_id)
             id
      FROM notification
      WHERE is_read = false
        AND resource_id IS NOT NULL
      ORDER BY user_id, type, resource_type, resource_id,
               COALESCE(updated_at, created_at) DESC
  )
  AND resource_id IS NOT NULL;

-- Step 2：创建部分唯一索引（只约束 resource_id 非空 + 未读状态的通知）
-- 排除 resource_id IS NULL 的系统通知（无聚合需求）
CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_unread_agg
    ON notification(user_id, type, resource_type, resource_id)
    WHERE is_read = false AND resource_id IS NOT NULL;

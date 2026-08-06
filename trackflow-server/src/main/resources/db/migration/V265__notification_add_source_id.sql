-- V265__notification_add_source_id.sql
-- 为 notification 表增加 source_id 字段，用于精准定位通知来源（如评论 ID）
-- 前端读取此字段构造 hash 锚点（#c_{source_id}），跳转工单详情后滚动并高亮目标评论

ALTER TABLE notification ADD COLUMN IF NOT EXISTS source_id BIGINT;
COMMENT ON COLUMN notification.source_id IS '来源子资源 ID（如触发 @提及 的评论 ID），前端用于 hash 精准定位';

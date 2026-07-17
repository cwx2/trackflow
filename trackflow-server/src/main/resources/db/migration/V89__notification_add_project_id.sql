-- V89: notification 表新增 project_id 列，支持按项目过滤通知
-- REQ-310-1

-- 1. 新增 project_id 列（NULL 允许，因为部分系统通知不关联项目）
ALTER TABLE notification ADD COLUMN project_id BIGINT REFERENCES project(id) ON DELETE SET NULL;

COMMENT ON COLUMN notification.project_id IS '通知关联的项目ID，NULL表示全局/系统通知';

-- 2. 回填已有通知的 project_id
-- resource_type='issue' 的通知通过 JOIN issue 表获取 project_id
UPDATE notification n
SET project_id = i.project_id
FROM issue i
WHERE n.resource_type = 'issue'
  AND n.resource_id = i.id
  AND n.project_id IS NULL;

-- resource_type='sprint' 的通知通过 JOIN sprint 表获取 project_id
UPDATE notification n
SET project_id = s.project_id
FROM sprint s
WHERE n.resource_type = 'sprint'
  AND n.resource_id = s.id
  AND n.project_id IS NULL;

-- resource_type='project' 的通知 resource_id 本身就是 project_id
UPDATE notification n
SET project_id = n.resource_id
WHERE n.resource_type = 'project'
  AND n.resource_id IS NOT NULL
  AND n.project_id IS NULL
  AND EXISTS (SELECT 1 FROM project p WHERE p.id = n.resource_id);

-- 3. 创建复合索引，支持按用户+项目+未读+时间排序的高效查询
CREATE INDEX idx_notif_user_project ON notification(user_id, project_id, is_read, created_at DESC);

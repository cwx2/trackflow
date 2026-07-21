-- V165__attachment_visibility.sql
-- 为附件增加可见性控制字段，对标 YouTrack "Attach files privately" 功能
-- NULL 表示全体项目成员可见；非空数组表示仅指定组的成员可见
-- 设计与 V164（评论可见性）保持一致

-- 使用 IF NOT EXISTS 确保幂等性（列可能已在早期手动迁移中添加）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'issue_attachment' AND column_name = 'visible_to_group_ids'
    ) THEN
        ALTER TABLE issue_attachment ADD COLUMN visible_to_group_ids BIGINT[] DEFAULT NULL;
    END IF;
END $$;

-- 索引：加速按可见性过滤的查询（GIN 索引支持数组操作符 @> && 等）
CREATE INDEX IF NOT EXISTS idx_issue_attachment_visibility ON issue_attachment USING GIN (visible_to_group_ids)
    WHERE visible_to_group_ids IS NOT NULL;

COMMENT ON COLUMN issue_attachment.visible_to_group_ids IS '可见性限制：NULL=全体可见，非空数组=仅指定组ID的成员可见';

-- 新增权限：查看私有附件/评论（拥有此权限的用户可以看到所有私有内容）
INSERT INTO sys_permission (code, name, description, category, scope, sort_order)
VALUES ('issue:read_private', '查看私有内容', '查看设置了可见性限制的附件和评论', 'issue', 'project', 65)
ON CONFLICT (code) DO NOTHING;

-- 为 project_admin 和 tech_lead 角色自动赋予该权限
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'issue:read_private'
FROM sys_role r WHERE r.code IN ('project_admin', 'tech_lead')
ON CONFLICT (role_id, permission) DO NOTHING;

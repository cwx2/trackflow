-- V164__comment_visibility.sql
-- 为评论增加可见性控制字段，对标 YouTrack "Visible to" 功能
-- NULL 表示全体项目成员可见；非空数组表示仅指定组的成员可见

ALTER TABLE issue_comment ADD COLUMN visible_to_group_ids BIGINT[] DEFAULT NULL;

-- 索引：加速按可见性过滤的查询（GIN 索引支持数组操作符 @> && 等）
CREATE INDEX idx_issue_comment_visibility ON issue_comment USING GIN (visible_to_group_ids)
    WHERE visible_to_group_ids IS NOT NULL;

COMMENT ON COLUMN issue_comment.visible_to_group_ids IS '可见性限制：NULL=全体可见，非空数组=仅指定组ID的成员可见';

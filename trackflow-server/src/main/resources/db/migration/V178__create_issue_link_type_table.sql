-- V178: 创建 issue_link_type 表，实现链接类型的数据库驱动管理
-- 参考 YouTrack Link Types 管理功能：支持管理员通过管理后台 CRUD 链接类型

-- 1. 创建 issue_link_type 表
CREATE TABLE issue_link_type (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    outward_name VARCHAR(100) NOT NULL,
    inward_name  VARCHAR(100) NOT NULL,
    direction   VARCHAR(20) NOT NULL CHECK (direction IN ('DIRECTED', 'UNDIRECTED', 'AGGREGATION')),
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一约束：name 必须唯一
CREATE UNIQUE INDEX idx_issue_link_type_name ON issue_link_type(name);

COMMENT ON TABLE issue_link_type IS '工单关联类型定义表';
COMMENT ON COLUMN issue_link_type.name IS '内部标识名称（用于 issue_link.link_type 引用）';
COMMENT ON COLUMN issue_link_type.outward_name IS '目标工单显示名（如 blocks、duplicates）';
COMMENT ON COLUMN issue_link_type.inward_name IS '源工单显示名（如 is blocked by、is duplicated by）';
COMMENT ON COLUMN issue_link_type.direction IS '方向类型：DIRECTED（有向）、UNDIRECTED（无向/对称）、AGGREGATION（聚合）';
COMMENT ON COLUMN issue_link_type.is_system IS '是否为系统内置类型（内置类型不可删除、不可修改方向）';

-- 2. 预置种子数据（对应当前硬编码的 7 种类型，合并为 4 种 Link Type）
-- 参考 YouTrack 默认类型：Depend(Directed), Duplicate(Aggregation), Relates(Undirected), Subtask(Aggregation)
INSERT INTO issue_link_type (name, outward_name, inward_name, direction, is_system) VALUES
    ('blocks', 'blocks', 'is blocked by', 'DIRECTED', TRUE),
    ('duplicates', 'duplicates', 'is duplicated by', 'AGGREGATION', TRUE),
    ('relates_to', 'relates to', 'relates to', 'UNDIRECTED', TRUE),
    ('parent_of', 'parent of', 'subtask of', 'AGGREGATION', TRUE)
ON CONFLICT DO NOTHING;

-- 3. 移除 issue_link 表的 CHECK 约束（V51 中添加的），改为通过应用层动态验证
ALTER TABLE issue_link DROP CONSTRAINT IF EXISTS chk_issue_link_type;

-- 4. 迁移现有数据：将 blocked_by、duplicated_by、child_of 统一转换为对应的正向类型
-- 因为在新模型中，反向名称由 issue_link_type 的 inward_name 提供，
-- 存储时统一使用正向类型名（outward 方向）
-- blocked_by → 反转为 blocks（交换 source 和 target）
UPDATE issue_link SET
    link_type = 'blocks',
    source_issue_id = target_issue_id,
    target_issue_id = source_issue_id
WHERE link_type = 'blocked_by';

-- duplicated_by → 反转为 duplicates（交换 source 和 target）
UPDATE issue_link SET
    link_type = 'duplicates',
    source_issue_id = target_issue_id,
    target_issue_id = source_issue_id
WHERE link_type = 'duplicated_by';

-- child_of → 反转为 parent_of（交换 source 和 target）
UPDATE issue_link SET
    link_type = 'parent_of',
    source_issue_id = target_issue_id,
    target_issue_id = source_issue_id
WHERE link_type = 'child_of';

-- V253: 将 State（状态）字段注册到自定义字段体系
-- 参照 YouTrack 行为：State 是 state 类型的自定义字段（enum 的特殊子类型）
-- 物理列 issue.status_id + issue_status 表保留不变，仅在 custom_field_definition 中注册使其可配置
-- 每个选项有额外的 is_resolved 属性，标记该状态是否视为"已解决"

-- 1. 为 custom_field_option 添加 is_resolved 列（仅 state 类型字段使用）
ALTER TABLE custom_field_option ADD COLUMN is_resolved BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN custom_field_option.is_resolved IS '仅 state 类型字段使用：标记该状态值是否视为"已解决"';

-- 2. 更新 field_format 约束，新增 state 类型
ALTER TABLE custom_field_definition DROP CONSTRAINT IF EXISTS ck_custom_field_format;
ALTER TABLE custom_field_definition
    ADD CONSTRAINT ck_custom_field_format
        CHECK (field_format IN ('string', 'text', 'int', 'float', 'date', 'datetime', 'bool', 'list', 'user', 'period', 'state'));

-- 3. 注册 State 自定义字段定义
INSERT INTO custom_field_definition (
    id, name, field_format, is_required, is_for_all, default_value,
    min_length, max_length, regexp, position, is_multi, is_hidden_in_list,
    aliases, is_private, is_auto_attach, sort_mode,
    created_at, updated_at
) VALUES (
    1000000000000000004, 'State', 'state', true, true, NULL,
    0, 0, NULL, -4, false, false,
    '状态,state,status', false, true, 'manual',
    NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 4. 从 issue_status 表同步选项到 custom_field_option
-- position 使用 issue_status.sort_order，is_default 来自 issue_status.is_default
-- is_resolved 来自 issue_status.is_closed（TrackFlow 中 is_closed 等价于 YouTrack 的 isResolved）
INSERT INTO custom_field_option (custom_field_id, project_id, value, position, is_default, color, description, is_archived, is_resolved, created_at, updated_at)
SELECT
    1000000000000000004,
    NULL,
    COALESCE(ist.display_name, ist.name),
    ist.sort_order,
    ist.is_default,
    ist.color,
    CASE ist.category
        WHEN 'open' THEN '开放状态'
        WHEN 'in_progress' THEN '进行中'
        WHEN 'done' THEN '已完成'
        WHEN 'cancelled' THEN '已取消'
        ELSE NULL
    END,
    false,
    ist.is_closed,
    NOW(),
    NOW()
FROM issue_status ist
WHERE NOT EXISTS (
    SELECT 1 FROM custom_field_option cfo
    WHERE cfo.custom_field_id = 1000000000000000004
      AND cfo.value = COALESCE(ist.display_name, ist.name)
      AND cfo.project_id IS NULL
)
ORDER BY ist.sort_order;

-- 5. 将 State 字段附加到所有现有项目
INSERT INTO custom_field_project (id, custom_field_id, project_id, position, is_excluded, has_independent_options, can_be_empty)
SELECT
    nextval('custom_field_project_id_seq'),
    1000000000000000004,
    p.id,
    -4,
    false,
    false,
    false
FROM project p
WHERE NOT EXISTS (
    SELECT 1 FROM custom_field_project cfp
    WHERE cfp.custom_field_id = 1000000000000000004 AND cfp.project_id = p.id
);

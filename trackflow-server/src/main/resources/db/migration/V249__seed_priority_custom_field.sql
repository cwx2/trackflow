-- V249: 将优先级从硬编码 VARCHAR 迁移为枚举类型自定义字段
-- 优先级是 YouTrack 中默认附加的枚举自定义字段，本迁移将其纳入 TrackFlow 的自定义字段体系

-- 1. 创建优先级自定义字段定义
INSERT INTO custom_field_definition (
    id, name, field_format, is_required, is_for_all, default_value,
    min_length, max_length, regexp, position, is_multi, is_hidden_in_list,
    aliases, is_private, is_auto_attach, sort_mode,
    created_at, updated_at
) VALUES (
    1000000000000000001, 'Priority', 'list', false, true, 'Normal',
    0, 0, NULL, -1, false, false,
    '优先级,priority', true, true, 'manual',
    NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2. 种子化优先级选项值（全局共享，project_id = NULL）
-- 位置从 0 开始，高优先级在前
INSERT INTO custom_field_option (id, custom_field_id, project_id, value, position, is_default, color, description, is_archived, created_at, updated_at) VALUES
    (1000000000000000101, 1000000000000000001, NULL, 'Show-stopper', 0, false, '#b91c1c', '阻塞性问题，必须立即解决', false, NOW(), NOW()),
    (1000000000000000102, 1000000000000000001, NULL, 'Critical',     1, false, '#ef4444', '严重问题，影响核心功能', false, NOW(), NOW()),
    (1000000000000000103, 1000000000000000001, NULL, 'High',         2, false, '#f59e0b', '高优先级，需要尽快处理', false, NOW(), NOW()),
    (1000000000000000104, 1000000000000000001, NULL, 'Normal',       3, true,  '#6366f1', '普通优先级，按计划处理', false, NOW(), NOW()),
    (1000000000000000105, 1000000000000000001, NULL, 'Low',          4, false, '#64748b', '低优先级，有空再处理', false, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3. 将优先级字段自动附加到所有现有项目
INSERT INTO custom_field_project (id, custom_field_id, project_id, position, is_excluded, has_independent_options, can_be_empty)
SELECT
    nextval('custom_field_project_id_seq'),
    1000000000000000001,
    p.id,
    -1,
    false,
    false,
    true
FROM project p
WHERE NOT EXISTS (
    SELECT 1 FROM custom_field_project cfp
    WHERE cfp.custom_field_id = 1000000000000000001 AND cfp.project_id = p.id
);

-- 4. 规范化现有 issue.priority 数据（修正大小写不一致）
UPDATE issue SET priority = 'Critical' WHERE priority = 'critical';
UPDATE issue SET priority = 'High' WHERE priority = 'high';
UPDATE issue SET priority = 'Normal' WHERE priority = 'normal';
UPDATE issue SET priority = 'Low' WHERE priority = 'low';

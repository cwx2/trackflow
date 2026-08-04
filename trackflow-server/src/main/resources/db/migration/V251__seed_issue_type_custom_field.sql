-- V251: 将工单类型从硬编码 VARCHAR 迁移为枚举类型自定义字段
-- 工单类型在 YouTrack 中是默认附加的枚举自定义字段，本迁移将其纳入 TrackFlow 的自定义字段体系
-- 使 issue type 可由项目管理员自定义（增删、调色、独立副本），不再受代码中的硬编码列表约束

-- 1. 创建工单类型自定义字段定义
INSERT INTO custom_field_definition (
    id, name, field_format, is_required, is_for_all, default_value,
    min_length, max_length, regexp, position, is_multi, is_hidden_in_list,
    aliases, is_private, is_auto_attach, sort_mode,
    created_at, updated_at
) VALUES (
    1000000000000000002, 'Type', 'list', true, true, 'Task',
    0, 0, NULL, -2, false, false,
    '类型,type,issue_type,issueType', false, true, 'manual',
    NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2. 种子化工单类型选项值（全局共享，project_id = NULL）
-- 位置从 0 开始，按使用频率排列
INSERT INTO custom_field_option (id, custom_field_id, project_id, value, position, is_default, color, description, is_archived, created_at, updated_at) VALUES
    (1000000000000000201, 1000000000000000002, NULL, 'Bug',     0, false, '#ef4444', '软件缺陷，需要修复', false, NOW(), NOW()),
    (1000000000000000202, 1000000000000000002, NULL, 'Task',    1, true,  '#6366f1', '常规任务', false, NOW(), NOW()),
    (1000000000000000203, 1000000000000000002, NULL, 'Feature', 2, false, '#22c55e', '新功能需求', false, NOW(), NOW()),
    (1000000000000000204, 1000000000000000002, NULL, 'Epic',    3, false, '#a855f7', '大型功能集合', false, NOW(), NOW()),
    (1000000000000000205, 1000000000000000002, NULL, 'Story',   4, false, '#3b82f6', '用户故事', false, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3. 将工单类型字段自动附加到所有现有项目
INSERT INTO custom_field_project (id, custom_field_id, project_id, position, is_excluded, has_independent_options, can_be_empty)
SELECT
    nextval('custom_field_project_id_seq'),
    1000000000000000002,
    p.id,
    -2,
    false,
    false,
    false
FROM project p
WHERE NOT EXISTS (
    SELECT 1 FROM custom_field_project cfp
    WHERE cfp.custom_field_id = 1000000000000000002 AND cfp.project_id = p.id
);

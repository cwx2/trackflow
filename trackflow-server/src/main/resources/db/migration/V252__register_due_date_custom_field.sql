-- V252: 将截止日期（Due Date）注册到自定义字段体系
-- 物理列 issue.due_date 保留不变，仅在 custom_field_definition 中注册使其可配置
-- 参照 YouTrack 行为：Due Date 默认私有(is_private=true)、默认不自动附加(is_auto_attach=false)
-- 为保持向后兼容，将其附加到所有现有项目

-- 1. 创建 Due Date 自定义字段定义（date 类型，无选项集）
INSERT INTO custom_field_definition (
    id, name, field_format, is_required, is_for_all, default_value,
    min_length, max_length, regexp, position, is_multi, is_hidden_in_list,
    aliases, is_private, is_auto_attach, sort_mode,
    created_at, updated_at
) VALUES (
    1000000000000000003, 'Due Date', 'date', false, true, NULL,
    0, 0, NULL, -3, false, false,
    '截止日期,due_date,dueDate,deadline', true, false, 'manual',
    NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2. 将 Due Date 字段附加到所有现有项目（向后兼容，当前系统中所有项目都显示 dueDate）
INSERT INTO custom_field_project (id, custom_field_id, project_id, position, is_excluded, has_independent_options, can_be_empty)
SELECT
    nextval('custom_field_project_id_seq'),
    1000000000000000003,
    p.id,
    -3,
    false,
    false,
    true
FROM project p
WHERE NOT EXISTS (
    SELECT 1 FROM custom_field_project cfp
    WHERE cfp.custom_field_id = 1000000000000000003 AND cfp.project_id = p.id
);

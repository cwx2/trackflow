-- V107: 清理自定义字段选项中的同名重复条目（活跃+归档并存）
-- 问题背景：选项更新策略在移除被引用选项时归档旧条目，然后创建同名新条目，导致重复
-- 修复策略：
--   1. 对于同字段内同名的活跃+归档对，如果归档条目未被工单引用则物理删除
--   2. 如果归档条目仍被工单引用，将引用迁移到活跃条目，然后删除归档条目

-- Step 1: 找出所有同字段同名的 活跃+归档 重复对，将归档条目中被引用的 ID 迁移到活跃条目
-- 更新 custom_field_value 中引用旧归档 option ID 的记录，改为引用同名的新活跃 option ID
UPDATE custom_field_value cfv
SET value = active_opt.id::text,
    updated_at = NOW()
FROM custom_field_option archived_opt
JOIN custom_field_option active_opt
    ON archived_opt.custom_field_id = active_opt.custom_field_id
    AND archived_opt.value = active_opt.value
    AND archived_opt.is_archived = true
    AND active_opt.is_archived = false
WHERE cfv.custom_field_id = archived_opt.custom_field_id
  AND cfv.value = archived_opt.id::text;

-- Step 2: 处理多选字段（值为逗号分隔的 ID 列表）中的归档 option ID 替换
-- 使用 replace 将旧 ID 替换为新 ID
UPDATE custom_field_value cfv
SET value = REPLACE(cfv.value, archived_opt.id::text, active_opt.id::text),
    updated_at = NOW()
FROM custom_field_option archived_opt
JOIN custom_field_option active_opt
    ON archived_opt.custom_field_id = active_opt.custom_field_id
    AND archived_opt.value = active_opt.value
    AND archived_opt.is_archived = true
    AND active_opt.is_archived = false
WHERE cfv.custom_field_id = archived_opt.custom_field_id
  AND cfv.value LIKE '%' || archived_opt.id::text || '%';

-- Step 3: 物理删除所有被同名活跃选项替代的归档条目
-- 此时所有引用已迁移到活跃选项，可以安全删除
DELETE FROM custom_field_option archived
USING custom_field_option active
WHERE archived.custom_field_id = active.custom_field_id
  AND archived.value = active.value
  AND archived.is_archived = true
  AND active.is_archived = false;

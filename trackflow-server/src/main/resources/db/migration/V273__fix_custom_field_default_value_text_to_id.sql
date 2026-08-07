-- V273: 修复自定义字段 default_value 从文本名称改为选项 ID
-- 问题根因：V249/V251 种子脚本误将选项文本（如 "Normal"、"Task"）存入 default_value，
-- 而后端验证器 ListFieldHandler 期望该值为数字类型的选项 ID。
-- 修复方式：通过 option 表查找匹配的选项 ID 替换文本值。

-- 修复 Priority 字段: "Normal" → 选项 ID
UPDATE custom_field_definition
SET default_value = (
    SELECT CAST(cfo.id AS TEXT)
    FROM custom_field_option cfo
    WHERE cfo.custom_field_id = custom_field_definition.id
      AND cfo.value = custom_field_definition.default_value
      AND cfo.is_archived = false
    LIMIT 1
)
WHERE id = 1000000000000000001
  AND default_value = 'Normal';

-- 修复 Type 字段: "Task" → 选项 ID
UPDATE custom_field_definition
SET default_value = (
    SELECT CAST(cfo.id AS TEXT)
    FROM custom_field_option cfo
    WHERE cfo.custom_field_id = custom_field_definition.id
      AND cfo.value = custom_field_definition.default_value
      AND cfo.is_archived = false
    LIMIT 1
)
WHERE id = 1000000000000000002
  AND default_value = 'Task';

-- 通用修复：任何 list/ownedField/version/state 类型字段如果 default_value 不是纯数字，
-- 尝试通过选项文本匹配转换为选项 ID
UPDATE custom_field_definition
SET default_value = (
    SELECT CAST(cfo.id AS TEXT)
    FROM custom_field_option cfo
    WHERE cfo.custom_field_id = custom_field_definition.id
      AND cfo.value = custom_field_definition.default_value
      AND cfo.is_archived = false
    LIMIT 1
)
WHERE field_format IN ('list', 'ownedField', 'version', 'state')
  AND default_value IS NOT NULL
  AND default_value != ''
  AND default_value !~ '^\d+$'
  AND EXISTS (
    SELECT 1 FROM custom_field_option cfo
    WHERE cfo.custom_field_id = custom_field_definition.id
      AND cfo.value = custom_field_definition.default_value
      AND cfo.is_archived = false
  );

-- 同样修复 custom_field_project 表中的项目级默认值
UPDATE custom_field_project cfp
SET default_value = (
    SELECT CAST(cfo.id AS TEXT)
    FROM custom_field_option cfo
    WHERE cfo.custom_field_id = cfp.custom_field_id
      AND cfo.value = cfp.default_value
      AND cfo.is_archived = false
    LIMIT 1
)
WHERE cfp.default_value IS NOT NULL
  AND cfp.default_value != ''
  AND cfp.default_value !~ '^\d+$'
  AND EXISTS (
    SELECT 1 FROM custom_field_definition cfd
    WHERE cfd.id = cfp.custom_field_id
      AND cfd.field_format IN ('list', 'ownedField', 'version', 'state')
  )
  AND EXISTS (
    SELECT 1 FROM custom_field_option cfo
    WHERE cfo.custom_field_id = cfp.custom_field_id
      AND cfo.value = cfp.default_value
      AND cfo.is_archived = false
  );

-- V162__add_custom_field_value_filter.sql
-- 为 custom_field_project 表增加值依赖过滤字段（Filter values based on 功能）
-- 当源字段的值满足条件时，目标字段只展示指定的选项子集
-- 这与现有的条件显示（conditionFieldId/conditionValues）是两个独立机制：
--   - 条件显示：控制字段本身是否出现
--   - 值依赖过滤：字段出现，但下拉选项被缩小

-- 值过滤依赖的源字段 ID（指向 custom_field_definition）
ALTER TABLE custom_field_project ADD COLUMN filter_field_id BIGINT;

-- 过滤规则 JSON
-- 格式: [{"whenValue": "optionId1", "showOnly": ["optionId3","optionId4"]}, ...]
-- 当源字段值为 whenValue 时，本字段只展示 showOnly 列表中的选项
ALTER TABLE custom_field_project ADD COLUMN filter_rules JSONB;

-- 添加外键约束
ALTER TABLE custom_field_project
    ADD CONSTRAINT fk_cfp_filter_field
    FOREIGN KEY (filter_field_id)
    REFERENCES custom_field_definition(id)
    ON DELETE SET NULL;

-- 添加注释
COMMENT ON COLUMN custom_field_project.filter_field_id IS '值过滤依赖的源字段 ID，为 NULL 表示无值过滤';
COMMENT ON COLUMN custom_field_project.filter_rules IS '过滤规则: 当源字段值为指定值时，本字段只展示指定选项。格式: [{"whenValue":"optionId","showOnly":["optionId1","optionId2"]}]';

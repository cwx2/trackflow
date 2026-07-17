-- REQ-215: 支持多值列表字段多行存储
-- 移除 UNIQUE(issue_id, custom_field_id) 约束，允许 is_multi 字段存储多行
-- 参考 OpenProject custom_values 表（无 UNIQUE 约束）

-- 1. 移除阻止多行存储的 UNIQUE 约束
ALTER TABLE custom_field_value DROP CONSTRAINT IF EXISTS uk_cf_value_issue_field;

-- 2. 添加普通联合索引（替代 UNIQUE，用于按 issue+field 查询）
CREATE INDEX IF NOT EXISTS idx_cf_value_issue_field ON custom_field_value(issue_id, custom_field_id);

-- 3. 添加按值筛选索引（用于精确匹配单个选项值）
CREATE INDEX IF NOT EXISTS idx_cf_value_field_value ON custom_field_value(custom_field_id, value);

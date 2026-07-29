-- V217__custom_field_option_project_scope.sql
-- 为自定义字段选项增加项目级独立副本能力（Make Independent Copy）
-- 参考 YouTrack 文档: https://www.jetbrains.com/help/youtrack/server/manage-custom-fields-per-project.html

-- 1. 添加 project_id 列（NULL = 共享选项集，具体值 = 项目独立副本）
ALTER TABLE custom_field_option ADD COLUMN project_id BIGINT REFERENCES project(id) ON DELETE CASCADE;
COMMENT ON COLUMN custom_field_option.project_id IS '所属项目ID，NULL表示全局共享选项集，非NULL表示项目独立副本';

-- 2. 删除原有的唯一索引（只包含 custom_field_id + value）
DROP INDEX IF EXISTS idx_cf_option_unique_value;
-- 原来可能没有此索引，忽略不存在的错误
-- 如果是 V24 或之后脚本创建的，名称可能不同

-- 3. 创建新的唯一索引（包含 project_id）
-- 允许全局选项和项目独立选项同时存在相同 value
CREATE UNIQUE INDEX idx_cf_option_unique_value_scoped 
    ON custom_field_option(custom_field_id, COALESCE(project_id, 0), value);
COMMENT ON INDEX idx_cf_option_unique_value_scoped IS '选项唯一性约束：同一字段+同一范围（全局或项目）内value唯一';

-- 4. 添加索引加速按项目查询选项
CREATE INDEX idx_cf_option_project ON custom_field_option(project_id) WHERE project_id IS NOT NULL;
COMMENT ON INDEX idx_cf_option_project IS '项目独立选项集快速查找';

-- 5. 更新 custom_field_project 表，添加选项集独立标记
ALTER TABLE custom_field_project ADD COLUMN has_independent_options BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN custom_field_project.has_independent_options IS '是否已创建项目级独立选项副本';

-- 备注：现有所有选项的 project_id = NULL，表示全局共享，升级后行为不变

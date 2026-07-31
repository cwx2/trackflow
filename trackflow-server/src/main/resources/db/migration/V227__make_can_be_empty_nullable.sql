-- V227__make_can_be_empty_nullable.sql
-- 修复 can_be_empty 列的定义：从 NOT NULL DEFAULT true 改为 NULLABLE
--
-- 业务语义：
-- - NULL: 继承全局设置（无项目级覆盖）
-- - true: 项目级覆盖，允许为空
-- - false: 项目级覆盖，不允许为空（Cannot be empty）
--
-- 此修复解决以下问题：
-- 1. clearOverride 时无法将 canBeEmpty 设置为 NULL（数据库报 NOT NULL 约束）
-- 2. hasOverride 判断逻辑无法正确区分"继承全局"与"显式设置为 true"

-- 1. 移除 NOT NULL 约束
ALTER TABLE custom_field_project
ALTER COLUMN can_be_empty DROP NOT NULL;

-- 2. 移除 DEFAULT 约束（新记录应由应用层决定是 NULL 还是具体值）
ALTER TABLE custom_field_project
ALTER COLUMN can_be_empty DROP DEFAULT;

-- 3. 将现有 true 值改为 NULL（恢复"继承全局"语义）
-- 只转换那些同时 is_required=NULL 且 default_value=NULL 的记录（这些记录原本就没有覆盖）
UPDATE custom_field_project
SET can_be_empty = NULL
WHERE can_be_empty = true
  AND is_required IS NULL
  AND default_value IS NULL;

-- 更新注释
COMMENT ON COLUMN custom_field_project.can_be_empty IS 
  '是否允许字段为空（项目级覆盖）。NULL = 继承全局设置，true = 允许为空，false = Cannot be empty';

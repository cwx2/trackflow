-- V294: 为 issue 表新增 priority_option_id 和 issue_type_option_id 列
-- 将 priority/issue_type 字符串值关联到 custom_field_option.id，支持 API 返回颜色

-- 1. 新增列
ALTER TABLE issue ADD COLUMN IF NOT EXISTS priority_option_id BIGINT;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS issue_type_option_id BIGINT;

-- 2. 数据迁移：从 value 字符串反查 option_id（优先级）
-- 先处理标准值
UPDATE issue i SET priority_option_id = (
    SELECT cfo.id FROM custom_field_option cfo
    WHERE cfo.custom_field_id = 1000000000000000001
      AND cfo.project_id IS NULL
      AND cfo.value = i.priority
    LIMIT 1
) WHERE i.priority IS NOT NULL AND i.priority_option_id IS NULL;

-- 处理兼容值：'normal' -> '普通'
UPDATE issue SET priority_option_id = (
    SELECT cfo.id FROM custom_field_option cfo
    WHERE cfo.custom_field_id = 1000000000000000001
      AND cfo.project_id IS NULL
      AND cfo.value = '普通'
    LIMIT 1
) WHERE LOWER(priority) IN ('normal', 'medium', '中') AND priority_option_id IS NULL;

-- 3. 数据迁移：从 value 字符串反查 option_id（工单类型）
UPDATE issue i SET issue_type_option_id = (
    SELECT cfo.id FROM custom_field_option cfo
    WHERE cfo.custom_field_id = 1000000000000000002
      AND cfo.project_id IS NULL
      AND cfo.value = i.issue_type
    LIMIT 1
) WHERE i.issue_type IS NOT NULL AND i.issue_type_option_id IS NULL;

-- 4. 索引
CREATE INDEX IF NOT EXISTS idx_issue_priority_option_id ON issue(priority_option_id);
CREATE INDEX IF NOT EXISTS idx_issue_issue_type_option_id ON issue(issue_type_option_id);

-- 5. 清理 custom_field_value 表中 priority/type 字段的冗余 EAV 数据
-- 这些内置字段现在直接存 issue 表的 option_id 列，不再需要 EAV 表中的冗余记录
DELETE FROM custom_field_value
WHERE custom_field_id IN (1000000000000000001, 1000000000000000002);

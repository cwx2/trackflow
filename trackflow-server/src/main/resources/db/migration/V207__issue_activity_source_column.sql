-- V207__issue_activity_source_column.sql
-- 为 issue_activity 表新增 source 字段，标识活动记录的来源（manual/automation/system 等）
-- 修复工作流自动化规则触发的活动流记录与用户手动操作无法区分的问题（REQ-630）

ALTER TABLE issue_activity ADD COLUMN IF NOT EXISTS source VARCHAR(50);
COMMENT ON COLUMN issue_activity.source IS '记录来源：manual（用户手动）/ automation（自动化规则）/ workflow_action（转换动作）/ system（系统）';

-- 反向填充：历史数据中 detail 包含 "source":"automation" 标识的记录
UPDATE issue_activity
SET source = 'automation'
WHERE source IS NULL
  AND detail::text LIKE '%"source":"automation"%';

-- 索引：按来源筛选（可选，低选择性列不强制）
-- CREATE INDEX idx_issue_activity_source ON issue_activity(source) WHERE source IS NOT NULL;

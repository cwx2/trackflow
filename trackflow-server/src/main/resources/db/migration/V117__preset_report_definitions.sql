-- V116: 预置基础 Issue 报表
-- 1) 新增 is_system 列标记系统报表（不可被普通用户删除）
-- 2) 插入 7 条标准报表（全局可见，无需项目归属）

-- Step 1: Add is_system column
ALTER TABLE report_definition ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;

-- Step 2: Insert preset system reports
-- 这些报表 projectId 为 NULL（全局），shared = true，created_by = NULL（系统生成）
INSERT INTO report_definition (name, project_id, type, config, shared, is_system, created_by, created_at, updated_at)
VALUES
  ('未完成任务', NULL, 'by_assignee', '{"groupBy":"assignee","filters":{"statusClosed":false}}', TRUE, TRUE, NULL, NOW(), NOW()),
  ('已完成任务（本月）', NULL, 'by_assignee', '{"groupBy":"assignee","filters":{"statusClosed":true,"periodType":"current_month"}}', TRUE, TRUE, NULL, NOW(), NOW()),
  ('未分配任务', NULL, 'by_status', '{"groupBy":"status","filters":{"unassigned":true}}', TRUE, TRUE, NULL, NOW(), NOW()),
  ('逾期任务', NULL, 'by_priority', '{"groupBy":"priority","filters":{"overdue":true,"statusClosed":false}}', TRUE, TRUE, NULL, NOW(), NOW()),
  ('本周新建', NULL, 'by_type', '{"groupBy":"type","filters":{"periodType":"current_week"}}', TRUE, TRUE, NULL, NOW(), NOW()),
  ('Sprint 进度', NULL, 'by_status', '{"groupBy":"status","filters":{"activeSprint":true}}', TRUE, TRUE, NULL, NOW(), NOW()),
  ('按优先级分布', NULL, 'by_priority', '{"groupBy":"priority","filters":{}}', TRUE, TRUE, NULL, NOW(), NOW())
ON CONFLICT DO NOTHING;

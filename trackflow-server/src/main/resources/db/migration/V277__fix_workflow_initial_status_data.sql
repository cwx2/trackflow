-- 修复工作流初始状态配置数据：全局默认初始状态不应为"Reopened"(id=7)
-- "Reopened"语义上是工单被关闭后再次打开的状态，不适合作为新建工单的初始状态
-- 正确的全局默认初始状态应为"Open"(id=1)，该状态在 issue_status 表中也标记为 is_default=true

UPDATE workflow_initial_status
SET status_id = (SELECT id FROM issue_status WHERE is_default = true LIMIT 1)
WHERE project_id IS NULL
  AND issue_type = '*'
  AND status_id = (SELECT id FROM issue_status WHERE name = 'Reopened' LIMIT 1);

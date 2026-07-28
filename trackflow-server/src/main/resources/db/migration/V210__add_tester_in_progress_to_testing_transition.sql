-- V210__add_tester_in_progress_to_testing_transition.sql
-- 为测试人员（tester）角色添加 In Progress → Testing 的全局工作流转换规则。
-- 修复测试人员将工单打回 In Progress 后无法重新提交测试的问题（REQ-713）。
--
-- 当前 tester 角色可以 Testing → In Progress（打回），但 In Progress 下没有任何可用转换，
-- 导致测试→打回→再测试循环中断。此迁移添加反向转换规则。

-- 1. 添加全局规则（workflow_definition_id=1，project_id IS NULL）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee, workflow_definition_id)
SELECT NULL, '*', 4, 2, 4, false, false, 1
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition
    WHERE workflow_definition_id = 1
      AND issue_type = '*'
      AND role_id = 4
      AND old_status_id = 2
      AND new_status_id = 4
      AND author = false
      AND assignee = false
);

-- 2. 为所有已有 tester 项目级工作流的项目同步添加此规则
--    这些项目已经有 tester 的其他转换规则（如 Testing→In Progress），需要补充反向规则
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee, workflow_definition_id)
SELECT DISTINCT wt.project_id, '*', 4, 2, 4, false, false, wt.workflow_definition_id
FROM workflow_transition wt
WHERE wt.role_id = 4
  AND wt.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM workflow_transition existing
      WHERE existing.workflow_definition_id = wt.workflow_definition_id
        AND existing.issue_type = '*'
        AND existing.role_id = 4
        AND existing.old_status_id = 2
        AND existing.new_status_id = 4
        AND existing.author = false
        AND existing.assignee = false
  );

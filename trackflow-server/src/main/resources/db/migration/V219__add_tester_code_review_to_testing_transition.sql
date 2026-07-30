-- V219__add_tester_code_review_to_testing_transition.sql
-- 为测试人员（tester）角色添加 Code Review → Testing 的全局工作流转换规则。
-- 修复测试人员无法在代码审查通过后主动接取工单进行测试的问题（REQ-878）。
--
-- 开发人员（developer）拥有以下到 Testing 的转换：
--   - In Progress → Testing
--   - Code Review → Testing (缺失！)
--   - Done (Local Env) → Testing
--
-- 测试人员（tester）目前只有：
--   - In Progress → Testing (V210 添加)
--   - Done (Local Env) → Testing
--
-- 此迁移补充 Code Review → Testing 转换，使测试人员工作流完整。

-- 状态 ID：Code Review = 3, Testing = 4
-- 角色 ID：tester = 4

-- 1. 添加全局规则（workflow_definition_id=1，project_id IS NULL）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee, workflow_definition_id)
SELECT NULL, '*', 4, 3, 4, false, false, 1
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition
    WHERE workflow_definition_id = 1
      AND issue_type = '*'
      AND role_id = 4
      AND old_status_id = 3
      AND new_status_id = 4
      AND author = false
      AND assignee = false
);

-- 2. 为所有已有 tester 项目级工作流的项目同步添加此规则
--    这些项目已经有 tester 的其他转换规则，需要补充此规则保持一致
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee, workflow_definition_id)
SELECT DISTINCT wt.project_id, '*', 4, 3, 4, false, false, wt.workflow_definition_id
FROM workflow_transition wt
WHERE wt.role_id = 4
  AND wt.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM workflow_transition existing
      WHERE existing.workflow_definition_id = wt.workflow_definition_id
        AND existing.issue_type = '*'
        AND existing.role_id = 4
        AND existing.old_status_id = 3
        AND existing.new_status_id = 4
        AND existing.author = false
        AND existing.assignee = false
  );

-- V228__fix_tester_testing_workflow_assignee_restriction.sql
-- 修复 REQ-992：测试人员无法通过属性面板将工单状态流转至"已完成"
--
-- 问题根因：
-- tester 角色的 Testing → Done / Testing → In Progress / Done → Reopened 规则
-- 设置了 assignee=true，导致只有当 tester 是工单负责人时才能执行这些转换。
-- 但在实际工作流中，tester 通常不是负责人（负责人是开发人员）。
--
-- 修复方案：
-- 将这些规则的 assignee 改为 false，使 tester 角色可以对任何处于 Testing 状态
-- 的工单执行测试相关的状态转换，不受负责人限制。
--
-- 影响范围：
-- 1. 全局规则（project_id IS NULL）
-- 2. 项目级规则（会覆盖全局规则，同样需要修复）

-- ==============================================================================
-- 1. 修复全局规则
-- ==============================================================================

-- Testing → Done (全局规则 id=102)
UPDATE workflow_transition
SET assignee = false
WHERE id = 102
  AND project_id IS NULL
  AND role_id = 4  -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 5; -- Done

-- Testing → In Progress (全局规则 id=2082264361134276610)
UPDATE workflow_transition
SET assignee = false
WHERE id = 2082264361134276610
  AND project_id IS NULL
  AND role_id = 4  -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 2; -- In Progress

-- Done → Reopened (全局规则 id=104)
-- 保持 assignee=true，因为重新打开工单需要更严格的权限控制
-- 仅当 tester 是负责人时才能重新打开（这是合理的业务逻辑）

-- ==============================================================================
-- 2. 修复项目级规则（这些规则优先于全局规则）
-- ==============================================================================

-- 批量更新所有项目级 tester Testing → Done 规则
UPDATE workflow_transition
SET assignee = false
WHERE project_id IS NOT NULL
  AND role_id = 4  -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 5  -- Done
  AND assignee = true;

-- 批量更新所有项目级 tester Testing → In Progress 规则
UPDATE workflow_transition
SET assignee = false
WHERE project_id IS NOT NULL
  AND role_id = 4  -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 2  -- In Progress
  AND assignee = true;

-- 注意：Done → Reopened 的项目级规则同样保持 assignee=true
-- 重新打开工单需要更严格的权限控制

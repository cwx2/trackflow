-- V213__restrict_tester_testing_transitions_to_assignee.sql
-- 限制 tester 角色的 Testing → Done 和 Testing → In Progress 转换仅 assignee 可执行
--
-- 魔法数字说明：
--   role_id=4 (tester), old_status_id=4 (Testing)
--   new_status_id=5 (Done), new_status_id=2 (In Progress)
--
-- 使用幂等方式：先删除冲突的旧记录（同组已有 assignee=true 的情况下），再 UPDATE 其余。
-- 注：所有处理范围限定在 author=false 规则，不影响 author=true 的规则。

-- ============================================================
-- 1. 处理全局规则（project_id IS NULL）
-- ============================================================

-- 删除全局 Testing → Done (role_id=4, old=4, new=5): assignee=false 记录（若同组已有 assignee=true 记录）
DELETE FROM workflow_transition wt
WHERE wt.project_id IS NULL
  AND wt.role_id = 4        -- tester
  AND wt.old_status_id = 4  -- Testing
  AND wt.new_status_id = 5  -- Done
  AND wt.author = false
  AND wt.assignee = false
  AND EXISTS (
    SELECT 1 FROM workflow_transition wt2
    WHERE wt2.project_id IS NULL
      AND wt2.issue_type = wt.issue_type
      AND wt2.role_id = 4
      AND wt2.old_status_id = 4
      AND wt2.new_status_id = 5
      AND wt2.author = false
      AND wt2.assignee = true
  );

-- 将剩余全局 Testing → Done: tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 5  -- Done
  AND author = false
  AND assignee = false;

-- 删除全局 Testing → In Progress (role_id=4, old=4, new=2): assignee=false 记录（若同组已有 assignee=true 记录）
DELETE FROM workflow_transition wt
WHERE wt.project_id IS NULL
  AND wt.role_id = 4        -- tester
  AND wt.old_status_id = 4  -- Testing
  AND wt.new_status_id = 2  -- In Progress
  AND wt.author = false
  AND wt.assignee = false
  AND EXISTS (
    SELECT 1 FROM workflow_transition wt2
    WHERE wt2.project_id IS NULL
      AND wt2.issue_type = wt.issue_type
      AND wt2.role_id = 4
      AND wt2.old_status_id = 4
      AND wt2.new_status_id = 2
      AND wt2.author = false
      AND wt2.assignee = true
  );

-- 将剩余全局 Testing → In Progress: tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 2  -- In Progress
  AND author = false
  AND assignee = false;

-- ============================================================
-- 2. 处理项目级规则
-- ============================================================

-- 删除项目级 Testing → Done: assignee=false 记录（若同 project/issue_type 组已有 assignee=true）
DELETE FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
  AND wt.role_id = 4        -- tester
  AND wt.old_status_id = 4  -- Testing
  AND wt.new_status_id = 5  -- Done
  AND wt.author = false
  AND wt.assignee = false
  AND EXISTS (
    SELECT 1 FROM workflow_transition wt2
    WHERE wt2.project_id = wt.project_id
      AND wt2.issue_type = wt.issue_type
      AND wt2.role_id = 4
      AND wt2.old_status_id = 4
      AND wt2.new_status_id = 5
      AND wt2.author = false
      AND wt2.assignee = true
  );

-- 将剩余项目级 Testing → Done: tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NOT NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 5  -- Done
  AND author = false
  AND assignee = false;

-- 删除项目级 Testing → In Progress: assignee=false 记录（若同 project/issue_type 组已有 assignee=true）
DELETE FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
  AND wt.role_id = 4        -- tester
  AND wt.old_status_id = 4  -- Testing
  AND wt.new_status_id = 2  -- In Progress
  AND wt.author = false
  AND wt.assignee = false
  AND EXISTS (
    SELECT 1 FROM workflow_transition wt2
    WHERE wt2.project_id = wt.project_id
      AND wt2.issue_type = wt.issue_type
      AND wt2.role_id = 4
      AND wt2.old_status_id = 4
      AND wt2.new_status_id = 2
      AND wt2.author = false
      AND wt2.assignee = true
  );

-- 将剩余项目级 Testing → In Progress: tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NOT NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 2  -- In Progress
  AND author = false
  AND assignee = false;

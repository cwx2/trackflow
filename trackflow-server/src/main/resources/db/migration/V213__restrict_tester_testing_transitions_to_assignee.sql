-- V213__restrict_tester_testing_transitions_to_assignee.sql
-- 限制 tester 角色的 Testing → Done 和 Testing → In Progress 转换仅 assignee 可执行
--
-- 问题：当前 tester 角色对 Testing 状态工单的 Done/In Progress 转换是基础规则（assignee=false），
-- 意味着任何 tester 都可以操作任意 Testing 状态的工单，即使该工单不是分配给他的。
-- 修复：将这些规则改为 assignee=true，使只有工单的负责人才能执行这些转换。
-- project_admin 和 product_manager 的规则保持不变（不受此限制）。

-- ============================================================
-- 1. 修改全局规则（project_id IS NULL）
-- ============================================================

-- Testing → Done: 将 tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 5  -- Done
  AND assignee = false;

-- Testing → In Progress（打回）: 将 tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 2  -- In Progress
  AND assignee = false;

-- ============================================================
-- 2. 修改所有项目级规则
-- ============================================================

-- Testing → Done: 项目级 tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NOT NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 5  -- Done
  AND assignee = false;

-- Testing → In Progress: 项目级 tester 基础规则改为 assignee-only
UPDATE workflow_transition
SET assignee = true
WHERE project_id IS NOT NULL
  AND role_id = 4        -- tester
  AND old_status_id = 4  -- Testing
  AND new_status_id = 2  -- In Progress
  AND assignee = false;

-- ============================================================
-- 3. 清理冗余规则（去重）
-- ============================================================
-- 修改后可能存在同一 (project_id, issue_type, role_id, old_status_id, new_status_id, assignee=true)
-- 的多条记录。保留 ID 最小的那条，删除其余。

DELETE FROM workflow_transition wt1
USING workflow_transition wt2
WHERE wt1.project_id IS NOT DISTINCT FROM wt2.project_id
  AND wt1.issue_type = wt2.issue_type
  AND wt1.role_id = wt2.role_id
  AND wt1.old_status_id = wt2.old_status_id
  AND wt1.new_status_id = wt2.new_status_id
  AND wt1.author = wt2.author
  AND wt1.assignee = wt2.assignee
  AND wt1.role_id = 4       -- tester
  AND wt1.old_status_id = 4 -- Testing
  AND wt1.assignee = true
  AND wt1.id > wt2.id;

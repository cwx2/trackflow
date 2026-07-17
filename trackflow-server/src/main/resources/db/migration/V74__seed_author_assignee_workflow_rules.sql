-- ============================================================
-- 为 author/assignee 维度提供默认种子数据（开箱即用）
-- 参考 OpenProject: app/seeders/basic_data/workflow_seeder.rb
--
-- 业务场景：
-- 1. 创建者可取消自己创建的工单（Open/Reopened → Cancelled）
-- 2. 负责人可直接将工单标记已解决（In Progress/Code Review/Testing → Done）
-- 3. 负责人可重新打开已完成的工单（Done → Reopened）
--
-- 角色 ID 参考（来自 V2__seed_roles.sql）：
--   3 = 开发人员 (developer)
--   4 = 测试人员 (tester)
--   6 = 产品经理 (product_manager)
--
-- 状态 ID 参考（来自 V6/V14 迁移）：
--   1 = Open, 2 = In Progress, 3 = Code Review
--   4 = Testing, 5 = Done, 6 = Cancelled, 7 = Reopened
-- ============================================================

-- -----------------------------------------------
-- 场景 1: 创建者（author）可取消自己的工单
-- 适用角色：开发人员、测试人员、产品经理
-- 转换路径：Open → Cancelled, Reopened → Cancelled
-- -----------------------------------------------

-- 开发人员 + author: Open → Cancelled
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 3, 1, 6, true, false)
ON CONFLICT DO NOTHING;

-- 开发人员 + author: Reopened → Cancelled
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 3, 7, 6, true, false)
ON CONFLICT DO NOTHING;

-- 测试人员 + author: Open → Cancelled
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 4, 1, 6, true, false)
ON CONFLICT DO NOTHING;

-- 测试人员 + author: Reopened → Cancelled
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 4, 7, 6, true, false)
ON CONFLICT DO NOTHING;

-- 产品经理 + author: Open → Cancelled
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 6, 1, 6, true, false)
ON CONFLICT DO NOTHING;

-- 产品经理 + author: Reopened → Cancelled
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 6, 7, 6, true, false)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------
-- 场景 2: 负责人（assignee）可直接标记完成
-- 适用角色：开发人员、测试人员
-- 转换路径：In Progress → Done, Code Review → Done, Testing → Done
-- -----------------------------------------------

-- 开发人员 + assignee: In Progress → Done
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 3, 2, 5, false, true)
ON CONFLICT DO NOTHING;

-- 开发人员 + assignee: Code Review → Done
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 3, 3, 5, false, true)
ON CONFLICT DO NOTHING;

-- 开发人员 + assignee: Testing → Done
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 3, 4, 5, false, true)
ON CONFLICT DO NOTHING;

-- 测试人员 + assignee: Testing → Done
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 4, 4, 5, false, true)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------
-- 场景 3: 负责人（assignee）可重新打开已完成的工单
-- 适用角色：开发人员、测试人员
-- 转换路径：Done → Reopened
-- -----------------------------------------------

-- 开发人员 + assignee: Done → Reopened
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 3, 5, 7, false, true)
ON CONFLICT DO NOTHING;

-- 测试人员 + assignee: Done → Reopened
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES (NULL, '*', 4, 5, 7, false, true)
ON CONFLICT DO NOTHING;

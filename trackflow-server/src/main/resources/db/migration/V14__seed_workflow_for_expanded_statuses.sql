-- ============================================================
-- 为 V13 扩充的状态(8-18)补充工作流转换规则
-- 角色: 2=project_admin, 3=developer, 4=tester
-- ============================================================

-- project_admin (role=2) 对新状态的转换规则
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id) VALUES
-- Todo(8) 可转到: In Progress(2), UI Todo(9), Cancelled(6)
(NULL, '*', 2, 8, 2), (NULL, '*', 2, 8, 9), (NULL, '*', 2, 8, 6),
-- UI Todo(9) 可转到: In Progress(2), Todo(8)
(NULL, '*', 2, 9, 2), (NULL, '*', 2, 9, 8),
-- Done Local(10) 可转到: Pending Code Review(12), Testing(4), No Test(11)
(NULL, '*', 2, 10, 12), (NULL, '*', 2, 10, 4), (NULL, '*', 2, 10, 11),
-- No Test(11) 可转到: Pending Publish(13), Done(5)
(NULL, '*', 2, 11, 13), (NULL, '*', 2, 11, 5),
-- Pending Code Review(12) 可转到: In Progress(2), Testing(4), Done Local(10)
(NULL, '*', 2, 12, 2), (NULL, '*', 2, 12, 4), (NULL, '*', 2, 12, 10),
-- Pending Publish(13) 可转到: Online(14), In Progress(2)
(NULL, '*', 2, 13, 14), (NULL, '*', 2, 13, 2),
-- Online(14) 可转到: Closed(16)
(NULL, '*', 2, 14, 16),
-- Solved(15) 可转到: Reopened(7), Closed(16)
(NULL, '*', 2, 15, 7), (NULL, '*', 2, 15, 16),
-- Closed(16) 可转到: Reopened(7)
(NULL, '*', 2, 16, 7),
-- Pending Cancel(17) 可转到: Cancelled(6), In Progress(2)
(NULL, '*', 2, 17, 6), (NULL, '*', 2, 17, 2),
-- Pending Extension(18) 可转到: In Progress(2), Todo(8)
(NULL, '*', 2, 18, 2), (NULL, '*', 2, 18, 8),
-- 从 In Progress(2) 增加到新状态的转换
(NULL, '*', 2, 2, 10), (NULL, '*', 2, 2, 11), (NULL, '*', 2, 2, 12),
-- 从 Open(1) 增加到 Todo(8)
(NULL, '*', 2, 1, 8), (NULL, '*', 2, 1, 9);

-- developer (role=3) 对新状态的转换规则
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id) VALUES
(NULL, '*', 3, 8, 2), (NULL, '*', 3, 8, 6),
(NULL, '*', 3, 9, 2), (NULL, '*', 3, 9, 8),
(NULL, '*', 3, 2, 10), (NULL, '*', 3, 2, 12),
(NULL, '*', 3, 10, 12), (NULL, '*', 3, 10, 4),
(NULL, '*', 3, 12, 2), (NULL, '*', 3, 12, 10),
(NULL, '*', 3, 13, 14);

-- tester (role=4) 对新状态的转换规则
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id) VALUES
(NULL, '*', 4, 4, 5), (NULL, '*', 4, 10, 4),
(NULL, '*', 4, 11, 5), (NULL, '*', 4, 11, 13);

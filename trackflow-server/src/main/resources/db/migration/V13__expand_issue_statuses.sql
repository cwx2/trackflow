-- 扩充 Issue 状态（对标 YouTrack 完整工作流）
-- 保留原有 7 条（id 1-7），新增 8 条

INSERT INTO issue_status (id, name, code, color, category, is_default, is_closed, sort_order) VALUES
(8, 'Todo', 'todo', '#42A5F5', 'open', false, false, 8),
(9, 'UI Todo', 'ui_todo', '#AB47BC', 'open', false, false, 9),
(10, 'Done (Local Env)', 'done_local', '#66BB6A', 'in_progress', false, false, 10),
(11, 'No Test', 'no_test', '#78909C', 'in_progress', false, false, 11),
(12, 'Pending Code Review', 'pending_code_review', '#7E57C2', 'in_progress', false, false, 12),
(13, 'Pending Publish', 'pending_publish', '#FFA726', 'in_progress', false, false, 13),
(14, 'Online', 'online', '#26A69A', 'done', false, true, 14),
(15, 'Solved', 'solved', '#43A047', 'done', false, true, 15),
(16, 'Closed', 'closed', '#546E7A', 'done', false, true, 16),
(17, 'Pending Cancel', 'pending_cancel', '#EF5350', 'open', false, false, 17),
(18, 'Pending Extension', 'pending_extension', '#FFCA28', 'open', false, false, 18);

SELECT setval('issue_status_id_seq', 100);

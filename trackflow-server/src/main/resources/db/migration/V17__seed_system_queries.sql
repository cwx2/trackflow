-- V17: 插入系统预设搜索条件（所有用户共享，pinned）
-- 使用第一个管理员用户作为所有者，shared = true 使所有用户可见

INSERT INTO saved_query (name, project_id, user_id, shared, pinned, folder, filters, sort_criteria, sort_order, created_at, updated_at) VALUES
-- 分配给我：assignee = 当前用户 且 状态未关闭
('分配给我', NULL, (SELECT id FROM sys_user ORDER BY id ASC LIMIT 1), true, true, NULL,
 '[{"field":"assignee","operator":"eq","value":["${currentUser}"]},{"field":"status","operator":"open","value":[]}]',
 '[{"field":"updatedAt","direction":"desc"}]',
 1, NOW(), NOW()),

-- 我报告的：reporter = 当前用户
('我报告的', NULL, (SELECT id FROM sys_user ORDER BY id ASC LIMIT 1), true, true, NULL,
 '[{"field":"reporter","operator":"eq","value":["${currentUser}"]}]',
 '[{"field":"createdAt","direction":"desc"}]',
 2, NOW(), NOW()),

-- 未解决：状态未关闭的所有工单
('未解决', NULL, (SELECT id FROM sys_user ORDER BY id ASC LIMIT 1), true, true, NULL,
 '[{"field":"status","operator":"open","value":[]}]',
 '[{"field":"updatedAt","direction":"desc"}]',
 3, NOW(), NOW()),

-- 最近更新：按更新时间降序（无筛选条件）
('最近更新', NULL, (SELECT id FROM sys_user ORDER BY id ASC LIMIT 1), true, true, NULL,
 '[]',
 '[{"field":"updatedAt","direction":"desc"}]',
 4, NOW(), NOW());

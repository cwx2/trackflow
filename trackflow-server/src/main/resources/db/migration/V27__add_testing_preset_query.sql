-- V27: 添加"待我测试"预设查询
-- 测试人员常用查询：筛选状态为 Testing 且分配给当前用户的工单

INSERT INTO saved_query (name, project_id, user_id, shared, pinned, folder, filters, sort_criteria, sort_order, created_at, updated_at)
SELECT '待我测试', NULL::bigint, (SELECT id FROM sys_user ORDER BY id ASC LIMIT 1),
       true, true, NULL::varchar,
       '[{"field":"status","operator":"in","value":["testing"]},{"field":"assignee","operator":"eq","value":["${currentUser}"]}]'::jsonb,
       '[{"field":"updatedAt","direction":"desc"}]'::jsonb,
       5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM saved_query sq WHERE sq.name = '待我测试' AND sq.shared = true);

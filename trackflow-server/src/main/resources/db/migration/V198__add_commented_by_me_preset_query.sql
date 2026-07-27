-- V198: 补充"我评论的"预设查询（YouTrack 标准配置中的 "Commented by me"）
-- 对标 YouTrack issues-list 侧边栏标准预设查询

INSERT INTO saved_query (name, project_id, user_id, shared, pinned, folder, filters, sort_criteria, sort_order, created_at, updated_at)
SELECT name, project_id, user_id, shared, pinned, folder, filters, sort_criteria, sort_order, created_at, updated_at
FROM (VALUES
  ('我评论的', NULL::bigint, (SELECT id FROM sys_user ORDER BY id ASC LIMIT 1), true, true, NULL::varchar,
   '[{"field":"commenter","operator":"eq","value":["${currentUser}"]}]'::jsonb,
   '[{"field":"updatedAt","direction":"desc"}]'::jsonb,
   5, NOW(), NOW())
) AS v(name, project_id, user_id, shared, pinned, folder, filters, sort_criteria, sort_order, created_at, updated_at)
WHERE NOT EXISTS (SELECT 1 FROM saved_query sq WHERE sq.name = v.name AND sq.shared = true);

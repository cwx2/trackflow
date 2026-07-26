-- V200: 为已存在的用户补充「我评论的」预设查询收藏记录
-- 背景：V198 新增了"我评论的"（id=25）共享查询，但由于所有用户已有收藏记录，
--       ensureDefaultFavorites 的"首次初始化"逻辑不会再触发，导致用户看不到该查询。
-- 修复：为所有有收藏记录但尚未收藏"我评论的"的用户，插入该收藏记录。

INSERT INTO user_query_favorite (user_id, query_id, sort_order, created_at)
SELECT
    uqf.user_id,
    sq.id AS query_id,
    -- sort_order = 该用户当前最大 sort_order + 1
    (SELECT COALESCE(MAX(sort_order), -1) + 1
     FROM user_query_favorite
     WHERE user_id = uqf.user_id) AS sort_order,
    NOW() AS created_at
FROM (
    -- 找到所有至少有一条收藏记录的用户（即已经过初始化的用户）
    SELECT DISTINCT user_id FROM user_query_favorite
) uqf
-- 获取"我评论的"查询 ID
CROSS JOIN (
    SELECT id FROM saved_query WHERE name = '我评论的' AND shared = true LIMIT 1
) sq
-- 排除已经收藏过的用户
WHERE NOT EXISTS (
    SELECT 1 FROM user_query_favorite
    WHERE user_id = uqf.user_id AND query_id = sq.id
);

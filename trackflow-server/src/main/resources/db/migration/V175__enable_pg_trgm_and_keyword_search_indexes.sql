-- V175__enable_pg_trgm_and_keyword_search_indexes.sql
-- 启用 pg_trgm 扩展并为工单搜索创建 trigram GIN 索引，
-- 使得 ILIKE '%keyword%' 查询可以走索引而非全表扫描。
-- 配合已有的 idx_issue_fulltext (tsvector GIN) 索引，实现中英文混合搜索性能优化。

-- 1. 启用 pg_trgm 扩展（支持 trigram 索引加速 ILIKE 查询）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. 为 issue.title 创建 trigram GIN 索引
-- 加速 title ILIKE '%keyword%' 查询（对中文子串匹配特别有效）
CREATE INDEX IF NOT EXISTS idx_issue_title_trgm
    ON issue USING gin (title gin_trgm_ops);

-- 3. 为 issue.description 创建 trigram GIN 索引
-- 加速 description ILIKE '%keyword%' 查询（确保中文关键词在描述中也能走索引）
CREATE INDEX IF NOT EXISTS idx_issue_description_trgm
    ON issue USING gin (description gin_trgm_ops);

-- 4. 为 sys_user.display_name 和 username 创建 trigram GIN 索引
-- 加速按负责人名称搜索（assignee_id IN 子查询中的 ILIKE）
CREATE INDEX IF NOT EXISTS idx_sys_user_display_name_trgm
    ON sys_user USING gin (display_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_sys_user_username_trgm
    ON sys_user USING gin (username gin_trgm_ops);

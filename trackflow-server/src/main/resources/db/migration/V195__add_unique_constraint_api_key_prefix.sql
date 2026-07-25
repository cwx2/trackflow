-- V195__add_unique_constraint_api_key_prefix.sql
-- 为 API Key prefix 字段添加 UNIQUE 约束，防止并发创建碰撞导致认证服务崩溃
-- 问题：idx_api_key_prefix 原为普通 B-tree 索引，并发 insert 时可能产生重复 prefix，
--       导致认证时 selectOne() 抛出 TooManyResultsException（500 错误）

-- 先检查是否存在重复数据（正常情况下不应有，此步骤为安全检查）
DO $$
DECLARE
    dup_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO dup_count
    FROM (
        SELECT prefix, COUNT(*) AS cnt
        FROM api_key
        GROUP BY prefix
        HAVING COUNT(*) > 1
    ) t;

    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Cannot add UNIQUE constraint: found % duplicate prefix value(s) in api_key table. Please deduplicate first.', dup_count;
    END IF;
END;
$$;

-- 删除旧的普通索引（如果存在）
DROP INDEX IF EXISTS idx_api_key_prefix;

-- 创建 UNIQUE 索引（同时具备索引加速和唯一性约束）
-- 使用 IF NOT EXISTS 保证幂等（手动执行过 DDL 后 Flyway 仍可安全执行）
CREATE UNIQUE INDEX IF NOT EXISTS idx_api_key_prefix ON api_key(prefix);

COMMENT ON COLUMN api_key.prefix IS 'API Key 前缀（唯一），用于快速查找，格式 tf_ + 8位随机字符';

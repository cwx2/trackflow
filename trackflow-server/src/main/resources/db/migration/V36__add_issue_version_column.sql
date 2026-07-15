-- V36: 添加乐观锁 version 列
-- 用于并发控制：每次更新 version+1，更新时 WHERE version = oldVersion
ALTER TABLE issue ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN issue.version IS '乐观锁版本号，每次更新自增';

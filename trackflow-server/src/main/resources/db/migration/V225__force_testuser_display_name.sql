-- V225__force_testuser_display_name.sql
-- 强制更新 testuser 的显示名称为 "系统管理员"
-- 背景：V224 迁移可能在 UserSyncService 从 JWT 同步后被覆盖回 "Test User"
-- 此脚本无条件更新，配合 UserSyncService 的 CJK 保护逻辑，确保不再被英文名覆盖

UPDATE sys_user
SET display_name = '系统管理员',
    updated_at = NOW()
WHERE username = 'testuser';

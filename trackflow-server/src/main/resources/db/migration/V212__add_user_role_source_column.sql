-- V212__add_user_role_source_column.sql
-- 为 user_role 表新增 source 字段，区分角色来源（keycloak 同步 vs 管理员手动分配）。
-- 修复 REQ-754：防止 Keycloak 反向同步撤销通过 TrackFlow 后台手动分配的全局角色。

ALTER TABLE user_role ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'manual';
COMMENT ON COLUMN user_role.source IS '角色分配来源: keycloak=Keycloak同步分配, manual=管理员后台手动分配';

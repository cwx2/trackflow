-- ============================================================
-- V160: Sprint 归档状态支持
-- REQ-65: Sprint 管理增加归档（Archive）与恢复（Restore）功能
-- ============================================================
-- Sprint 的 status 字段为 varchar(20)，无 CHECK 约束，
-- 直接在枚举中新增 'archived' 值即可，无需 DDL。
-- 此脚本仅做注释性标记，确认归档功能的数据库兼容性。

-- 确认：Sprint status 列当前允许的值为 'planned', 'active', 'completed'，
-- 新增 'archived' 表示已归档（从主列表隐藏，但数据保留可恢复）。
-- 无需修改表结构。

COMMENT ON COLUMN sprint.status IS 'Sprint 状态: planned(计划中), active(进行中), completed(已完成), archived(已归档)';

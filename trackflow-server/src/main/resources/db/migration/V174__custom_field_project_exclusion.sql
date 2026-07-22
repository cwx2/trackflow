-- V174__custom_field_project_exclusion.sql
-- 支持全局字段（isForAll=true）从单个项目中排除
-- YouTrack 行为：即使是 Auto-attach 全局字段，也可从项目中移除并清除所有值

ALTER TABLE custom_field_project ADD COLUMN is_excluded BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN custom_field_project.is_excluded IS '是否为排除记录。true 表示全局字段被从此项目排除，移除时清除了值';

-- 索引：按 project_id + is_excluded 快速过滤排除的字段
CREATE INDEX idx_cfp_project_excluded ON custom_field_project(project_id, is_excluded) WHERE is_excluded = true;

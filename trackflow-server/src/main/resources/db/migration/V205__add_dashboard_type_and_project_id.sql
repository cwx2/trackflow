-- V205__add_dashboard_type_and_project_id.sql
-- 为 dashboard 表增加 dashboard_type 和 project_id 字段，支持项目概览仪表盘
-- dashboard_type 可选值：'personal'（默认）、'project_overview'
-- project_id 关联 project 表，project_overview 类型时必填

ALTER TABLE dashboard
    ADD COLUMN IF NOT EXISTS dashboard_type VARCHAR(50) NOT NULL DEFAULT 'personal',
    ADD COLUMN IF NOT EXISTS project_id BIGINT NULL;

COMMENT ON COLUMN dashboard.dashboard_type IS '仪表盘类型：personal=个人仪表盘, project_overview=项目概览';
COMMENT ON COLUMN dashboard.project_id IS '所属项目 ID（dashboard_type=project_overview 时必填）';

-- 为 project_overview 类型建立唯一约束，确保每个项目只有一个概览仪表盘
CREATE UNIQUE INDEX IF NOT EXISTS idx_dashboard_project_overview
    ON dashboard (project_id)
    WHERE dashboard_type = 'project_overview';

-- 为 project_id 添加索引（查询项目概览仪表盘用）
CREATE INDEX IF NOT EXISTS idx_dashboard_project_id
    ON dashboard (project_id)
    WHERE project_id IS NOT NULL;

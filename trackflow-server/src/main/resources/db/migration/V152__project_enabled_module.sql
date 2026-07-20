-- ============================================================================
-- V152: 项目启用模块表 (project_enabled_module)
-- 参考 OpenProject enabled_modules 机制
-- 每个项目可独立启用/禁用功能模块，权限检查时取模块过滤后的交集
-- ============================================================================

-- 1. 创建模块表
CREATE TABLE project_enabled_module (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    module_name VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(project_id, module_name)
);

CREATE INDEX idx_project_enabled_module_project ON project_enabled_module(project_id);

COMMENT ON TABLE project_enabled_module IS '项目启用模块——控制哪些功能模块在该项目中生效';
COMMENT ON COLUMN project_enabled_module.module_name IS '模块名称，对应 sys_permission.category (issue/sprint/time_tracking/report/integration/query/project)';

-- 2. 为所有现有项目启用全部模块（向后兼容）
-- 定义全部可选模块：issue, sprint, time_tracking, report, integration, query, project
INSERT INTO project_enabled_module (project_id, module_name, created_at)
SELECT p.id, m.module_name, now()
FROM project p
CROSS JOIN (
    VALUES ('issue'), ('sprint'), ('time_tracking'), ('report'), ('integration'), ('query'), ('project')
) AS m(module_name)
ON CONFLICT DO NOTHING;

-- 3. 在 sys_permission 表上为 category 列添加注释（已有字段，仅加注释）
COMMENT ON COLUMN sys_permission.category IS '权限所属模块（映射到 project_enabled_module.module_name），用于模块过滤';

-- Issue Template table: project-scoped templates for quick issue creation
CREATE TABLE issue_template (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,               -- Markdown content skeleton
    issue_type      VARCHAR(50),        -- Default issue type (Task/Bug/Feature/Improvement)
    priority        VARCHAR(50),        -- Default priority
    default_tags    JSONB,              -- Array of tag IDs to auto-assign: ["1","2"]
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,  -- System preset templates (cannot delete)
    sort_order      INT NOT NULL DEFAULT 0,
    created_by      BIGINT REFERENCES sys_user(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

-- Index for fast lookup by project
CREATE INDEX idx_issue_template_project ON issue_template(project_id) WHERE deleted = FALSE;

-- Unique name per project (soft-delete aware)
CREATE UNIQUE INDEX idx_issue_template_name_unique ON issue_template(project_id, name) WHERE deleted = FALSE;

-- Seed 3 system default templates for all existing projects
INSERT INTO issue_template (project_id, name, description, issue_type, priority, is_system, sort_order, created_at, updated_at)
SELECT
    p.id,
    '功能需求',
    E'## 用户场景\n\n**作为** [角色]，\n**我想要** [操作]，\n**以便** [目的]。\n\n## 验收标准\n\n- [ ] \n- [ ] \n\n## 技术约束\n\n（如有技术限制或依赖，请说明）',
    'Feature',
    'Normal',
    TRUE,
    1,
    NOW(), NOW()
FROM project p
ON CONFLICT DO NOTHING;

INSERT INTO issue_template (project_id, name, description, issue_type, priority, is_system, sort_order, created_at, updated_at)
SELECT
    p.id,
    'Bug 报告',
    E'## 环境信息\n\n- 浏览器/版本：\n- 操作系统：\n- 发生时间：\n\n## 复现步骤\n\n1. \n2. \n3. \n\n## 实际结果\n\n\n\n## 期望结果\n\n\n\n## 截图/日志\n\n',
    'Bug',
    'High',
    TRUE,
    2,
    NOW(), NOW()
FROM project p
ON CONFLICT DO NOTHING;

INSERT INTO issue_template (project_id, name, description, issue_type, priority, is_system, sort_order, created_at, updated_at)
SELECT
    p.id,
    '技术任务',
    E'## 任务目标\n\n简述此技术任务要解决的问题。\n\n## 实现方案\n\n### 方案描述\n\n\n\n### 涉及模块\n\n- \n\n## 影响范围\n\n- 是否需要数据库迁移：\n- 是否影响现有 API：\n- 是否需要配置变更：\n\n## 完成定义\n\n- [ ] 代码实现完成\n- [ ] 单元测试通过\n- [ ] 代码审核通过',
    'Task',
    'Normal',
    TRUE,
    3,
    NOW(), NOW()
FROM project p
ON CONFLICT DO NOTHING;

-- Grant template management permission to project_admin and tech_lead
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'project:manage_templates'
FROM sys_role r WHERE r.code IN ('project_admin', 'tech_lead')
ON CONFLICT DO NOTHING;

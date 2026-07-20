-- =============================================================
-- V141: 引入 workflow_definition 实体层
-- 
-- 目的：为工作流转换规则添加上层容器（命名的工作流定义），
--       支持工作流的命名、克隆、共享（多项目复用同一工作流）。
--
-- 数据模型变化：
--   workflow_definition (新) → 工作流定义（名称、描述、是否默认）
--   project_workflow (新)    → 项目与工作流定义的绑定关系
--   workflow_transition      → 新增 workflow_definition_id FK
--
-- 迁移策略：
--   1. 创建新表
--   2. 为现有全局规则创建"系统默认工作流"定义
--   3. 为每个项目的规则集创建独立工作流定义
--   4. 回填 workflow_transition.workflow_definition_id
--   5. 创建 project_workflow 绑定关系
-- =============================================================

-- 1. 创建 workflow_definition 表
CREATE TABLE workflow_definition (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_by  BIGINT,
    updated_by  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE workflow_definition IS '工作流定义（命名的工作流容器）';
COMMENT ON COLUMN workflow_definition.name IS '工作流名称（如"标准开发流程"、"简化流程"）';
COMMENT ON COLUMN workflow_definition.is_default IS '是否为系统默认工作流（新项目自动绑定）';

-- 2. 创建 project_workflow 关联表（多对多：一个项目可绑定多个工作流，一个工作流可被多项目共享）
CREATE TABLE project_workflow (
    id                       BIGSERIAL PRIMARY KEY,
    project_id               BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    workflow_definition_id   BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, workflow_definition_id)
);

COMMENT ON TABLE project_workflow IS '项目与工作流定义的绑定关系';

-- 3. 给 workflow_transition 添加 workflow_definition_id 列（可空，向后兼容）
ALTER TABLE workflow_transition
    ADD COLUMN workflow_definition_id BIGINT REFERENCES workflow_definition(id) ON DELETE CASCADE;

COMMENT ON COLUMN workflow_transition.workflow_definition_id IS '所属工作流定义（迁移过渡期可空，后续强制非空）';

-- 4. 数据迁移：为全局规则创建"系统默认工作流"
INSERT INTO workflow_definition (name, description, is_default, created_at, updated_at)
VALUES ('系统默认工作流', '系统内置的默认工作流定义，适用于所有标准项目。', TRUE, NOW(), NOW());

-- 5. 回填全局规则的 workflow_definition_id
UPDATE workflow_transition
SET workflow_definition_id = (SELECT id FROM workflow_definition WHERE is_default = TRUE LIMIT 1)
WHERE project_id IS NULL;

-- 6. 为每个有项目级规则的项目创建独立工作流定义
-- 使用 DO 块逐项目处理
DO $$
DECLARE
    proj RECORD;
    def_id BIGINT;
    proj_name TEXT;
BEGIN
    FOR proj IN
        SELECT DISTINCT wt.project_id
        FROM workflow_transition wt
        WHERE wt.project_id IS NOT NULL
    LOOP
        -- 获取项目名称
        SELECT p.name INTO proj_name FROM project p WHERE p.id = proj.project_id;
        IF proj_name IS NULL THEN
            proj_name := '项目 ' || proj.project_id;
        END IF;

        -- 创建工作流定义
        INSERT INTO workflow_definition (name, description, is_default, created_at, updated_at)
        VALUES (proj_name || ' 工作流', '从项目"' || proj_name || '"的既有规则自动迁移生成。', FALSE, NOW(), NOW())
        RETURNING id INTO def_id;

        -- 回填该项目规则的 workflow_definition_id
        UPDATE workflow_transition
        SET workflow_definition_id = def_id
        WHERE project_id = proj.project_id;

        -- 创建项目与工作流的绑定
        INSERT INTO project_workflow (project_id, workflow_definition_id, created_at)
        VALUES (proj.project_id, def_id, NOW());
    END LOOP;
END $$;

-- 7. 为没有项目级规则的项目绑定系统默认工作流
INSERT INTO project_workflow (project_id, workflow_definition_id, created_at)
SELECT p.id, (SELECT id FROM workflow_definition WHERE is_default = TRUE LIMIT 1), NOW()
FROM project p
WHERE p.id NOT IN (SELECT project_id FROM project_workflow);

-- 8. 创建索引
CREATE INDEX idx_workflow_transition_definition_id ON workflow_transition(workflow_definition_id);
CREATE INDEX idx_project_workflow_project_id ON project_workflow(project_id);
CREATE INDEX idx_project_workflow_definition_id ON project_workflow(workflow_definition_id);
CREATE INDEX idx_workflow_definition_is_default ON workflow_definition(is_default) WHERE is_default = TRUE;

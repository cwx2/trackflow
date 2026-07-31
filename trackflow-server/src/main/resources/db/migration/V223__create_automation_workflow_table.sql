-- V223__create_automation_workflow_table.sql
-- Agent 工作流自动化表，用于存储可视化工作流定义

CREATE TABLE automation_workflow (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    definition  JSONB NOT NULL DEFAULT '{}',
    created_by  BIGINT REFERENCES sys_user(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE automation_workflow IS 'Agent 工作流定义';
COMMENT ON COLUMN automation_workflow.id IS '主键';
COMMENT ON COLUMN automation_workflow.name IS '工作流名称';
COMMENT ON COLUMN automation_workflow.description IS '工作流描述';
COMMENT ON COLUMN automation_workflow.definition IS '工作流画布 JSON（nodes + edges + variables）';
COMMENT ON COLUMN automation_workflow.created_by IS '创建人';
COMMENT ON COLUMN automation_workflow.created_at IS '创建时间';
COMMENT ON COLUMN automation_workflow.updated_at IS '更新时间';

-- 索引
CREATE INDEX idx_automation_workflow_created_by ON automation_workflow(created_by);
CREATE INDEX idx_automation_workflow_updated_at ON automation_workflow(updated_at DESC);

-- 创建工作流初始状态配置表
-- 存储每个（项目, 工单类型）组合的默认初始状态
-- 解耦"初始状态"概念与"状态转换规则"，使语义更清晰

CREATE TABLE workflow_initial_status (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT,
    issue_type  VARCHAR(50) NOT NULL DEFAULT '*',
    status_id   BIGINT NOT NULL REFERENCES issue_status(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  BIGINT,
    CONSTRAINT uk_workflow_initial_status UNIQUE (project_id, issue_type)
);

COMMENT ON TABLE workflow_initial_status IS '工作流初始状态配置：定义每个项目+工单类型组合创建工单时的默认初始状态';
COMMENT ON COLUMN workflow_initial_status.project_id IS '项目 ID，NULL 表示全局默认配置';
COMMENT ON COLUMN workflow_initial_status.issue_type IS '工单类型，* 表示该项目所有类型的默认值';
COMMENT ON COLUMN workflow_initial_status.status_id IS '初始状态 ID，引用 issue_status.id';
COMMENT ON COLUMN workflow_initial_status.created_by IS '配置人';

CREATE INDEX idx_workflow_initial_status_project ON workflow_initial_status(project_id);

-- V229__add_automation_execution_tables.sql
-- 为工作流编辑器升级添加执行记录表，支持 DAG 执行引擎的运行日志存储

-- 工作流执行记录主表
CREATE TABLE automation_execution (
    id              BIGSERIAL PRIMARY KEY,
    automation_id   BIGINT       NOT NULL REFERENCES automation_workflow(id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'running'
                        CHECK (status IN ('running', 'success', 'failed', 'cancelled')),
    input           JSONB,           -- 工作流触发时的输入参数快照
    output          JSONB,           -- 工作流最终输出结果
    error_message   TEXT,            -- 失败时的错误信息
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMP,
    duration_ms     BIGINT,          -- 总耗时（毫秒）
    created_by      BIGINT       REFERENCES sys_user(id) ON DELETE SET NULL
);

COMMENT ON TABLE automation_execution IS '工作流执行记录主表，每次触发执行创建一条记录';
COMMENT ON COLUMN automation_execution.status IS '执行状态: running/success/failed/cancelled';
COMMENT ON COLUMN automation_execution.input  IS '触发时的输入参数快照（JSONB）';
COMMENT ON COLUMN automation_execution.output IS '工作流最终输出结果（JSONB）';

-- 节点执行记录表（每个节点每次执行一条）
CREATE TABLE automation_node_execution (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT       NOT NULL REFERENCES automation_execution(id) ON DELETE CASCADE,
    node_id         VARCHAR(100) NOT NULL,   -- 对应 WorkflowNode.id
    node_type       VARCHAR(50)  NOT NULL,   -- 对应 WorkflowNode.type
    node_name       VARCHAR(200),            -- 对应 WorkflowNode.nodeMeta.title
    status          VARCHAR(20)  NOT NULL DEFAULT 'running'
                        CHECK (status IN ('running', 'success', 'failed', 'skipped')),
    input           JSONB,           -- 该节点实际接收到的输入（VariableRef 已解析为真实值）
    output          JSONB,           -- 该节点产出的输出
    error_info      TEXT,            -- 节点执行失败时的错误详情
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMP,
    duration_ms     BIGINT           -- 节点执行耗时（毫秒）
);

COMMENT ON TABLE automation_node_execution IS '工作流节点执行记录，每个节点每次运行一条';
COMMENT ON COLUMN automation_node_execution.node_id   IS '对应 WorkflowDefinition.nodes[].id';
COMMENT ON COLUMN automation_node_execution.node_type IS '节点类型：cli-agent/variables/condition/delay 等';
COMMENT ON COLUMN automation_node_execution.input     IS '实际输入快照，VariableRef 已解析为真实值';
COMMENT ON COLUMN automation_node_execution.output    IS '节点输出快照，用于调试和历史回放';

-- 索引
CREATE INDEX idx_automation_execution_automation_id  ON automation_execution(automation_id);
CREATE INDEX idx_automation_execution_status         ON automation_execution(status);
CREATE INDEX idx_automation_execution_created_by     ON automation_execution(created_by);
CREATE INDEX idx_automation_node_execution_exec_id   ON automation_node_execution(execution_id);
CREATE INDEX idx_automation_node_execution_node_id   ON automation_node_execution(node_id);

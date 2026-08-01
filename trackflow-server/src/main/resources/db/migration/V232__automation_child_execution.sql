ALTER TABLE automation_execution
    ADD COLUMN IF NOT EXISTS parent_execution_id BIGINT REFERENCES automation_execution(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS parent_node_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_automation_execution_parent
    ON automation_execution(parent_execution_id, parent_node_id)
    WHERE parent_execution_id IS NOT NULL;

COMMENT ON COLUMN automation_execution.parent_execution_id IS '子工作流所属父执行';
COMMENT ON COLUMN automation_execution.parent_node_id IS '发起子工作流的父节点';

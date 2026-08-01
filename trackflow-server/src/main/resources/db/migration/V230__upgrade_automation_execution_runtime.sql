-- V230__upgrade_automation_execution_runtime.sql
-- 升级自动化执行状态模型，支持分支跳过、检查点、取消、等待和后续恢复。

ALTER TABLE automation_execution
    DROP CONSTRAINT IF EXISTS automation_execution_status_check;

ALTER TABLE automation_execution
    ADD CONSTRAINT automation_execution_status_check
        CHECK (status IN (
            'queued', 'running', 'waiting_timer', 'waiting_event', 'waiting_approval',
            'retrying', 'paused', 'success', 'failed', 'cancelled'
        ));

ALTER TABLE automation_execution
    ADD COLUMN IF NOT EXISTS checkpoint JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS wake_up_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS actor_user_id BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS definition_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS definition_snapshot JSONB;

COMMENT ON COLUMN automation_execution.checkpoint IS '持久化执行检查点，保存节点输出和边状态';
COMMENT ON COLUMN automation_execution.heartbeat_at IS '执行器最后心跳时间，用于故障恢复';
COMMENT ON COLUMN automation_execution.wake_up_at IS '定时等待节点的唤醒时间';
COMMENT ON COLUMN automation_execution.actor_user_id IS '自动化实际执行身份';
COMMENT ON COLUMN automation_execution.correlation_id IS '关联一次业务处理链路的幂等标识';
COMMENT ON COLUMN automation_execution.definition_snapshot IS '执行开始时冻结的工作流定义，恢复时不受草稿修改影响';

ALTER TABLE automation_node_execution
    DROP CONSTRAINT IF EXISTS automation_node_execution_status_check;

ALTER TABLE automation_node_execution
    ADD CONSTRAINT automation_node_execution_status_check
        CHECK (status IN (
            'pending', 'ready', 'running', 'waiting', 'retrying',
            'success', 'failed', 'skipped', 'cancelled'
        ));

ALTER TABLE automation_node_execution
    ADD COLUMN IF NOT EXISTS attempt INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(30),
    ADD COLUMN IF NOT EXISTS selected_ports JSONB,
    ADD COLUMN IF NOT EXISTS retryable BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN automation_node_execution.attempt IS '节点执行尝试次数，从 1 开始';
COMMENT ON COLUMN automation_node_execution.outcome IS '节点结果语义，例如 success/timeout/rejected';
COMMENT ON COLUMN automation_node_execution.selected_ports IS '本次执行选中的控制流输出端口';

CREATE INDEX IF NOT EXISTS idx_automation_execution_wake_up
    ON automation_execution(status, wake_up_at)
    WHERE status IN ('waiting_timer', 'retrying');

CREATE INDEX IF NOT EXISTS idx_automation_execution_correlation
    ON automation_execution(correlation_id)
    WHERE correlation_id IS NOT NULL;

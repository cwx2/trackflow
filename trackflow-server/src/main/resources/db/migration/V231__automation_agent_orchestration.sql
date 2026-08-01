-- TrackFlow 自主 Agent 编排：发布态、角色、工作领取租约、审批与外部事件收件箱。

ALTER TABLE automation_workflow
    ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'draft',
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS published_definition JSONB,
    ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(30) NOT NULL DEFAULT 'manual',
    ADD COLUMN IF NOT EXISTS trigger_config JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS concurrency_mode VARCHAR(20) NOT NULL DEFAULT 'queue',
    ADD COLUMN IF NOT EXISTS max_concurrent INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS actor_user_id BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

ALTER TABLE automation_workflow
    ADD CONSTRAINT automation_workflow_status_check
        CHECK (status IN ('draft', 'published', 'disabled')),
    ADD CONSTRAINT automation_workflow_trigger_type_check
        CHECK (trigger_type IN ('manual', 'schedule', 'issue_created', 'issue_changed', 'webhook')),
    ADD CONSTRAINT automation_workflow_concurrency_mode_check
        CHECK (concurrency_mode IN ('queue', 'skip', 'parallel')),
    ADD CONSTRAINT automation_workflow_max_concurrent_check
        CHECK (max_concurrent BETWEEN 1 AND 50);

CREATE INDEX IF NOT EXISTS idx_automation_workflow_trigger
    ON automation_workflow(status, trigger_type, project_id);

CREATE TABLE automation_role_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    provider_type VARCHAR(30) NOT NULL DEFAULT 'cli',
    model VARCHAR(100),
    system_prompt TEXT NOT NULL DEFAULT '',
    tool_policy JSONB NOT NULL DEFAULT '{}',
    output_schema JSONB NOT NULL DEFAULT '{}',
    workspace_policy JSONB NOT NULL DEFAULT '{}',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT automation_role_provider_check
        CHECK (provider_type IN ('cli', 'http', 'openai_compatible'))
);

CREATE TABLE automation_work_item (
    id BIGSERIAL PRIMARY KEY,
    automation_id BIGINT NOT NULL REFERENCES automation_workflow(id) ON DELETE CASCADE,
    issue_id BIGINT REFERENCES issue(id) ON DELETE CASCADE,
    correlation_id VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'queued',
    priority INTEGER NOT NULL DEFAULT 0,
    lease_owner VARCHAR(120),
    lease_token VARCHAR(100),
    lease_expires_at TIMESTAMP,
    attempt INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMP NOT NULL DEFAULT NOW(),
    execution_id BIGINT REFERENCES automation_execution(id) ON DELETE SET NULL,
    last_error TEXT,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT automation_work_item_state_check
        CHECK (state IN ('queued', 'leased', 'running', 'completed', 'failed', 'dead_letter', 'cancelled')),
    CONSTRAINT automation_work_item_attempt_check
        CHECK (attempt >= 0 AND max_attempts BETWEEN 1 AND 20),
    CONSTRAINT uq_automation_work_item_correlation UNIQUE (automation_id, correlation_id)
);

CREATE INDEX idx_automation_work_item_claim
    ON automation_work_item(state, available_at, priority DESC, created_at)
    WHERE state IN ('queued', 'leased');
CREATE INDEX idx_automation_work_item_issue ON automation_work_item(issue_id);

CREATE TABLE automation_approval (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES automation_execution(id) ON DELETE CASCADE,
    node_execution_id BIGINT REFERENCES automation_node_execution(id) ON DELETE CASCADE,
    node_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'medium',
    request_payload JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    requested_by BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    decided_by BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    decision_comment TEXT,
    expires_at TIMESTAMP,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT automation_approval_status_check
        CHECK (status IN ('pending', 'approved', 'rejected', 'expired', 'cancelled')),
    CONSTRAINT automation_approval_risk_check
        CHECK (risk_level IN ('low', 'medium', 'high', 'critical'))
);

CREATE UNIQUE INDEX uq_automation_approval_pending_node
    ON automation_approval(execution_id, node_id) WHERE status = 'pending';
CREATE INDEX idx_automation_approval_status ON automation_approval(status, created_at);

CREATE TABLE automation_event_inbox (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    issue_id BIGINT REFERENCES issue(id) ON DELETE CASCADE,
    payload JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    error_message TEXT,
    CONSTRAINT uq_automation_event_key UNIQUE (event_type, event_key)
);

CREATE INDEX idx_automation_event_unprocessed
    ON automation_event_inbox(occurred_at) WHERE processed_at IS NULL;

COMMENT ON TABLE automation_role_profile IS '可复用 Agent 角色、工具白名单、输出约束和工作区策略';
COMMENT ON TABLE automation_work_item IS '自动化待处理工作及可续租领取信息，防止多个执行器重复处理';
COMMENT ON TABLE automation_approval IS '高风险节点的人工审批请求';
COMMENT ON TABLE automation_event_inbox IS '可幂等消费的 TrackFlow 领域事件收件箱';

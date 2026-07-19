-- ============================================================
-- V124: Workflow On-change Rule Engine
-- 工作流自动化规则引擎：字段变更时自动执行动作
-- ============================================================

-- 规则定义表
CREATE TABLE workflow_rule (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT REFERENCES project(id) ON DELETE CASCADE,  -- NULL=全局规则
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    rule_type       VARCHAR(20) NOT NULL DEFAULT 'on_change',          -- 规则类型
    trigger_event   VARCHAR(50) NOT NULL,                              -- 'issue_created' / 'field_changed'
    trigger_field   VARCHAR(50),                                       -- 触发字段名, NULL=所有字段
    condition_json  JSONB NOT NULL DEFAULT '[]'::jsonb,                -- 前置条件数组
    action_json     JSONB NOT NULL DEFAULT '[]'::jsonb,                -- 执行动作数组
    enabled         BOOLEAN NOT NULL DEFAULT true,
    sort_order      INT NOT NULL DEFAULT 0,
    created_by      BIGINT REFERENCES sys_user(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引：按项目+事件类型查询
CREATE INDEX idx_workflow_rule_project_event ON workflow_rule(project_id, trigger_event) WHERE enabled = true;
-- 索引：全局规则查询
CREATE INDEX idx_workflow_rule_global_event ON workflow_rule(trigger_event) WHERE project_id IS NULL AND enabled = true;

COMMENT ON TABLE workflow_rule IS '工作流自动化规则定义';
COMMENT ON COLUMN workflow_rule.project_id IS '关联项目，NULL表示全局规则';
COMMENT ON COLUMN workflow_rule.rule_type IS '规则类型：on_change=字段变更触发';
COMMENT ON COLUMN workflow_rule.trigger_event IS '触发事件：issue_created=工单创建, field_changed=字段变更';
COMMENT ON COLUMN workflow_rule.trigger_field IS '触发字段名（field_changed时指定），NULL=任意字段变更都触发';
COMMENT ON COLUMN workflow_rule.condition_json IS '前置条件JSON数组，如 [{"field":"type","operator":"equals","value":"Bug"}]';
COMMENT ON COLUMN workflow_rule.action_json IS '执行动作JSON数组，如 [{"type":"set_field","field":"priority","value":"Critical"}]';

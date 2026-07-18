-- =====================================================================
-- V116: Rule Engine & Scoring System
-- 通用规则引擎：支持配置化规则定义、自动/手动执行、计分统计
-- =====================================================================

-- 规则定义表
CREATE TABLE rule_definition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    project_id BIGINT REFERENCES project(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,

    -- 触发类型
    trigger_type VARCHAR(50) NOT NULL,
    trigger_config JSONB NOT NULL DEFAULT '{}',

    -- 计算公式
    score_formula VARCHAR(50) NOT NULL,
    score_config JSONB NOT NULL DEFAULT '{}',

    -- 作用对象字段
    target_field VARCHAR(50) NOT NULL DEFAULT 'assignee',

    -- 执行配置
    schedule_cron VARCHAR(100),
    dedup_strategy VARCHAR(50) NOT NULL DEFAULT 'daily',

    -- 通知动作
    actions JSONB DEFAULT '[]',

    -- 审计
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_rule_trigger_type CHECK (trigger_type IN ('scheduled', 'event', 'manual')),
    CONSTRAINT chk_rule_score_formula CHECK (score_formula IN ('linear_daily', 'fixed', 'cumulative_increment', 'custom')),
    CONSTRAINT chk_rule_target_field CHECK (target_field IN ('assignee', 'reporter', 'created_by')),
    CONSTRAINT chk_rule_dedup_strategy CHECK (dedup_strategy IN ('daily', 'once_per_issue', 'no_dedup'))
);

CREATE INDEX idx_rule_def_project ON rule_definition(project_id);
CREATE INDEX idx_rule_def_enabled ON rule_definition(enabled) WHERE enabled = true;

-- 规则执行记录表
CREATE TABLE rule_execution_log (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rule_definition(id) ON DELETE CASCADE,
    issue_id BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    target_user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    score DECIMAL(10,2) NOT NULL DEFAULT 0,
    score_detail JSONB,
    executed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    execution_date DATE NOT NULL DEFAULT CURRENT_DATE,
    note TEXT
);

CREATE INDEX idx_rule_exec_user ON rule_execution_log(target_user_id, executed_at DESC);
CREATE INDEX idx_rule_exec_rule ON rule_execution_log(rule_id, executed_at DESC);
CREATE INDEX idx_rule_exec_issue ON rule_execution_log(issue_id);
CREATE INDEX idx_rule_exec_date ON rule_execution_log(execution_date);
-- 去重索引：同一规则+同一 Issue+同一天 只能有一条记录（daily 策略）
CREATE UNIQUE INDEX idx_rule_exec_dedup_daily ON rule_execution_log(rule_id, issue_id, execution_date);

-- 权限种子：rule:manage（管理规则）、rule:view_statistics（查看统计）
INSERT INTO sys_permission (code, name, category, scope, description, sort_order)
VALUES
    ('rule:manage', '管理规则', 'rule', 'global', '创建、编辑、删除、启用/禁用规则', 1),
    ('rule:view_statistics', '查看规则统计', 'rule', 'global', '查看规则执行记录和统计数据', 2)
ON CONFLICT DO NOTHING;

-- 将 rule:manage 和 rule:view_statistics 授予 system_admin (role_id=1)
INSERT INTO role_permission (role_id, permission)
SELECT 1, 'rule:manage'
WHERE NOT EXISTS (SELECT 1 FROM role_permission WHERE role_id = 1 AND permission = 'rule:manage');

INSERT INTO role_permission (role_id, permission)
SELECT 1, 'rule:view_statistics'
WHERE NOT EXISTS (SELECT 1 FROM role_permission WHERE role_id = 1 AND permission = 'rule:view_statistics');

-- 将 rule:view_statistics 授予 project_admin (role_id=2)
INSERT INTO role_permission (role_id, permission)
SELECT 2, 'rule:view_statistics'
WHERE NOT EXISTS (SELECT 1 FROM role_permission WHERE role_id = 2 AND permission = 'rule:view_statistics');

-- 种子数据：默认的"零食分享（过期罚款）"规则
INSERT INTO rule_definition (name, description, trigger_type, trigger_config, score_formula, score_config,
                             target_field, schedule_cron, dedup_strategy, actions)
VALUES (
    '零食分享（过期罚款）',
    '工单过期未完成自动计算罚款，每天5元递增。每天9:00自动扫描。',
    'scheduled',
    '{"issueFilter": {"statuses_not_in_category": ["closed"], "has_due_date": true, "due_date_before": "now"}}'::jsonb,
    'linear_daily',
    '{"base_amount": 5, "field": "due_date"}'::jsonb,
    'assignee',
    '0 0 9 * * ?',
    'daily',
    '[{"type": "notification", "title_template": "工单 {issueKey} 已逾期 {overdueDays} 天，当前罚款 {score} 元"}]'::jsonb
);

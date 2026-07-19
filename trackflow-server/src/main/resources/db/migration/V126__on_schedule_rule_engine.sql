-- ============================================================
-- V126: On-schedule 规则引擎支持
-- 扩展 workflow_rule 表，新增 execution_log 表
-- ============================================================

-- 1) 给 workflow_rule 表增加调度相关字段
ALTER TABLE workflow_rule
    ADD COLUMN IF NOT EXISTS cron_expression VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_executed_at TIMESTAMP;

-- 2) 新建执行日志表
CREATE TABLE IF NOT EXISTS workflow_rule_execution_log (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        BIGINT NOT NULL REFERENCES workflow_rule(id) ON DELETE CASCADE,
    executed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    matched_count  INT NOT NULL DEFAULT 0,
    success_count  INT NOT NULL DEFAULT 0,
    failure_count  INT NOT NULL DEFAULT 0,
    error_message  TEXT,
    duration_ms    INT
);

-- 索引：按规则+时间查最近执行
CREATE INDEX IF NOT EXISTS idx_rule_exec_log_rule_time
    ON workflow_rule_execution_log(rule_id, executed_at DESC);

-- 3) 允许 workflow_rule 表 rule_type 为 on_schedule
COMMENT ON COLUMN workflow_rule.cron_expression IS '调度表达式，如 daily/weekly/hourly 或标准 cron';
COMMENT ON COLUMN workflow_rule.last_executed_at IS '上次执行时间';
COMMENT ON TABLE workflow_rule_execution_log IS 'On-schedule 规则执行日志';

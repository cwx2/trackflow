-- V262__add_issue_key_to_workflow_rule_execution_log.sql
-- 为 workflow_rule_execution_log 表增加 issue_key 字段，支持 on-change 规则记录触发的工单标识

ALTER TABLE workflow_rule_execution_log ADD COLUMN issue_key VARCHAR(50);
COMMENT ON COLUMN workflow_rule_execution_log.issue_key IS '触发规则的工单 Key（on-change 规则为单个工单，on-schedule 规则为 NULL）';

-- 为 issue_key 建索引，方便按工单过滤日志
CREATE INDEX idx_wf_rule_exec_log_issue_key ON workflow_rule_execution_log(issue_key) WHERE issue_key IS NOT NULL;

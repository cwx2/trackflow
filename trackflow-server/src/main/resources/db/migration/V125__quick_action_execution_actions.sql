-- V124: Add execution_actions column to quick_action_definition
-- Supports REQ-503-2: Workflow Action rules with automated actions
-- execution_actions stores a list of actions to execute automatically:
-- [{"type":"set_field","field":"priority","value":"Critical"},
--  {"type":"add_tag","tagName":"urgent"},
--  {"type":"add_comment","content":"Escalated by automation"},
--  {"type":"send_notification","template":"escalation"}]

ALTER TABLE quick_action_definition
    ADD COLUMN IF NOT EXISTS execution_actions JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Add action_type to distinguish form-based (legacy) vs rule-based (new) actions
-- 'form': requires user to fill form before execution (existing behavior)
-- 'rule': executes automated actions immediately on click (new behavior)
ALTER TABLE quick_action_definition
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(20) NOT NULL DEFAULT 'form';

COMMENT ON COLUMN quick_action_definition.execution_actions IS 'Automated actions to execute: set_field, add_tag, add_comment, send_notification, set_status';
COMMENT ON COLUMN quick_action_definition.action_type IS 'form = requires user input; rule = executes immediately';

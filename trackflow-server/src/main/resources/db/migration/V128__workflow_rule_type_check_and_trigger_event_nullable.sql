-- REQ-525: Add CHECK constraint on rule_type and make trigger_event nullable for on_schedule rules
-- 1. Add CHECK constraint on rule_type
ALTER TABLE workflow_rule
    ADD CONSTRAINT chk_workflow_rule_type CHECK (rule_type IN ('on_change', 'on_schedule'));

-- 2. Make trigger_event nullable (on_schedule rules don't need it)
ALTER TABLE workflow_rule
    ALTER COLUMN trigger_event DROP NOT NULL;

-- 3. Fix any existing on_schedule rules: set trigger_event to NULL
UPDATE workflow_rule
SET trigger_event = NULL
WHERE rule_type = 'on_schedule';

-- 4. Add a CHECK: on_change rules must have trigger_event, on_schedule must have cron_expression
ALTER TABLE workflow_rule
    ADD CONSTRAINT chk_workflow_rule_trigger_consistency CHECK (
        (rule_type = 'on_change' AND trigger_event IS NOT NULL) OR
        (rule_type = 'on_schedule' AND cron_expression IS NOT NULL)
    );

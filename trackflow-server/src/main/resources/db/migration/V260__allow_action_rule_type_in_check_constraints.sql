-- V260: 更新 CHECK 约束以支持 Action Rule 类型
-- Bug 修复: V259 新增了 action_command 字段但未更新 V128 添加的 CHECK 约束，
-- 导致 rule_type='action' 的记录无法插入。

-- 1. 删除旧的 rule_type 枚举约束
ALTER TABLE workflow_rule DROP CONSTRAINT IF EXISTS chk_workflow_rule_type;

-- 2. 重建约束，增加 'action' 类型
ALTER TABLE workflow_rule
    ADD CONSTRAINT chk_workflow_rule_type CHECK (rule_type IN ('on_change', 'on_schedule', 'action'));

-- 3. 删除旧的触发器一致性约束
ALTER TABLE workflow_rule DROP CONSTRAINT IF EXISTS chk_workflow_rule_trigger_consistency;

-- 4. 重建触发器一致性约束，覆盖 action 类型
-- action 类型必须有 action_command，on_change 必须有 trigger_event，on_schedule 必须有 cron_expression
ALTER TABLE workflow_rule
    ADD CONSTRAINT chk_workflow_rule_trigger_consistency CHECK (
        (rule_type = 'on_change' AND trigger_event IS NOT NULL) OR
        (rule_type = 'on_schedule' AND cron_expression IS NOT NULL) OR
        (rule_type = 'action' AND action_command IS NOT NULL)
    );

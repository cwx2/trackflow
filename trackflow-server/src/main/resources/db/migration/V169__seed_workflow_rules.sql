-- ============================================================
-- V169: Seed workflow_rule 示例数据
-- 提供 2 条全局示例规则，供管理员参考规则配置方式
-- ============================================================

-- 获取 testuser 的 ID 作为创建者
DO $$
DECLARE
    v_creator_id BIGINT;
BEGIN
    SELECT id INTO v_creator_id FROM sys_user WHERE username = 'testuser' LIMIT 1;
    IF v_creator_id IS NULL THEN
        v_creator_id := 1; -- fallback
    END IF;

    -- 1. on_change 规则示例：新建 Bug 类型工单自动设置优先级为 Normal
    INSERT INTO workflow_rule (
        project_id, name, description, rule_type, trigger_event, trigger_field,
        condition_json, action_json, enabled, sort_order,
        cron_expression, last_executed_at, created_by, created_at, updated_at
    ) VALUES (
        NULL,  -- 全局规则
        '新建 Bug 自动设置 Normal 优先级',
        '当创建 Bug 类型的工单时，如果没有指定优先级，自动将优先级设置为 Normal。作为示例规则供参考。',
        'on_change',
        'issue_created',
        NULL,  -- 所有字段
        '[{"field":"type","operator":"equals","value":"Bug"},{"field":"priority","operator":"is_empty"}]'::jsonb,
        '[{"type":"set_field","field":"priority","value":"Normal"}]'::jsonb,
        false,  -- 默认禁用，管理员手动启用
        10,
        NULL,
        NULL,
        v_creator_id,
        NOW(),
        NOW()
    ) ON CONFLICT DO NOTHING;

    -- 2. on_schedule 规则示例：每天检查超期工单并添加提醒评论
    INSERT INTO workflow_rule (
        project_id, name, description, rule_type, trigger_event, trigger_field,
        condition_json, action_json, enabled, sort_order,
        cron_expression, last_executed_at, created_by, created_at, updated_at
    ) VALUES (
        NULL,  -- 全局规则
        '每日检查超期工单并提醒',
        '每天自动检查所有已逾期且未关闭的工单，添加提醒评论。作为示例规则供参考。',
        'on_schedule',
        NULL,  -- on_schedule 不使用 trigger_event
        NULL,
        '[{"field":"due_date","operator":"overdue"},{"field":"status","operator":"not_equals","value":"Closed"}]'::jsonb,
        '[{"type":"add_comment","content":"⚠️ 此工单已超过截止日期，请尽快处理。（由自动化规则 {{rule_name}} 生成）"}]'::jsonb,
        false,  -- 默认禁用
        20,
        'daily',
        NULL,
        v_creator_id,
        NOW(),
        NOW()
    ) ON CONFLICT DO NOTHING;

END $$;

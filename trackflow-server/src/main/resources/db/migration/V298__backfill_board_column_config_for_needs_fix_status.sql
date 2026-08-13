-- V298__backfill_board_column_config_for_needs_fix_status.sql
-- 为已有看板列配置但缺少 "Needs Fix" (status_id=19) 记录的项目补充配置行。
-- 这修复了新增状态后已初始化看板的项目中工单"消失"的问题。

INSERT INTO board_column_config (id, project_id, status_id, field_value, visible, sort_order, collapsed, wip_min, wip_max, created_at, updated_at)
SELECT
    nextval('board_column_config_id_seq'),
    bcc.project_id,
    19,
    NULL,
    true,
    -- 放在 Testing(status_id=4) 之后：找到 Testing 的 sort_order + 1
    (SELECT COALESCE(MAX(c2.sort_order), 3) + 1
     FROM board_column_config c2
     WHERE c2.project_id = bcc.project_id AND c2.status_id = 4),
    false,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM (
    -- 找出所有有 board_column_config 但缺少 status_id=19 的项目
    SELECT DISTINCT project_id
    FROM board_column_config
    WHERE project_id NOT IN (
        SELECT project_id FROM board_column_config WHERE status_id = 19
    )
) bcc;

-- V285__fix_dashboard_widget_data_consistency.sql
-- REQ-453: 修复系统默认仪表盘中"待处理"数字卡片和"待处理工单"列表 Widget 数据口径矛盾
--
-- 问题：
--   - NumberCardWidget (queryType=open, 无 projectId) 统计所有项目的 open 工单总数 → 114
--   - IssueListWidget  (queryType=my_open, projectId=某特定项目) 仅查"分配给我+特定项目" → 0
--   - 两者标题都含"待处理"，但查询口径完全不同，造成用户困惑
--
-- 修复策略：
--   1. 将 IssueListWidget 的 queryType 改为 "open"（与数字卡片一致），移除 projectId 限制
--   2. 更新标题为"待处理工单（全部）"明确其范围
--   3. 同时更新数字卡片标题为"待处理（全部）"确保与列表对应

-- 修复 issue_list Widget：对齐为全局 open，与 number_card 一致
UPDATE dashboard_widget
SET config = jsonb_build_object(
        'queryType', 'open',
        'pageSize', 10,
        'sort', '-updatedAt'
    ),
    title = '待处理工单'
WHERE id = 2
  AND dashboard_id = (SELECT id FROM dashboard WHERE is_system_default = TRUE LIMIT 1)
  AND widget_type = 'issue_list';

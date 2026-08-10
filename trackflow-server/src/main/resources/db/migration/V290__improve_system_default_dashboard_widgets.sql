-- V290__improve_system_default_dashboard_widgets.sql
-- REQ-486: 改进系统默认仪表盘，为用户提供个性化的"分配给我"工单视图
--
-- 问题：系统默认仪表盘的 issue_list 显示所有 open 工单，对产品经理等用户缺乏个性化价值
-- 修复：将 issue_list widget 改为 my_open（分配给当前用户的未关闭工单），提供开箱即用的个人视图

-- 更新现有的 issue_list Widget：改为"分配给我的工单"
UPDATE dashboard_widget
SET config = '{"queryType":"my_open","pageSize":10,"sort":"-updatedAt"}'::jsonb,
    title = '分配给我的工单'
WHERE id = 2
  AND dashboard_id = (SELECT id FROM dashboard WHERE is_system_default = TRUE LIMIT 1)
  AND widget_type = 'issue_list';

-- V211__fix_default_dashboard_issue_list_config.sql
-- 修复系统默认仪表盘 Issue List Widget 缺少预设查询的问题
-- 确保所有 issue_list 类型的 Widget 在系统默认仪表盘中有合理的 queryType 配置
-- 参考 YouTrack：默认仪表盘包含一个 Issue List Widget，配置为"所有工单，按更新时间排序"

-- 更新系统默认仪表盘中没有 queryType 配置的 issue_list Widget
-- 设置为 queryType=all（显示所有工单，按更新时间倒序）
UPDATE dashboard_widget
SET config = '{"queryType":"all","pageSize":10}'::jsonb,
    title = CASE WHEN title = 'Issue 列表' THEN '最近更新' ELSE title END
WHERE dashboard_id = (SELECT id FROM dashboard WHERE is_system_default = TRUE LIMIT 1)
  AND widget_type = 'issue_list'
  AND (config ->> 'queryType' IS NULL);

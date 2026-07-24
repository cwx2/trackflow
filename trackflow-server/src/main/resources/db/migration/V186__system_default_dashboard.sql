-- V186__system_default_dashboard.sql
-- 添加系统默认仪表盘概念：每个站点有一个全局默认仪表盘，所有用户可见，管理员可编辑
-- 参考 YouTrack Default Dashboard 功能

-- 1. 添加 is_system_default 列
ALTER TABLE dashboard ADD COLUMN is_system_default BOOLEAN NOT NULL DEFAULT FALSE;

-- 确保最多只有一个系统默认仪表盘（使用 unique partial index）
CREATE UNIQUE INDEX idx_dashboard_system_default
    ON dashboard (is_system_default) WHERE is_system_default = TRUE;

-- 2. 插入系统默认仪表盘（owner_id 使用第一个 system_admin 用户，如果没有则使用第一个用户）
INSERT INTO dashboard (name, description, owner_id, shared, layout, is_system_default, layout_version)
SELECT
    'TrackFlow Dashboard',
    '系统默认仪表盘 — 所有用户的起始页面，管理员可自由编辑',
    COALESCE(
        (SELECT su.id FROM sys_user su
         JOIN global_member gm ON gm.user_id = su.id
         JOIN sys_role sr ON sr.id = gm.role_id
         WHERE sr.code = 'system_admin'
         ORDER BY su.created_at ASC
         LIMIT 1),
        (SELECT id FROM sys_user ORDER BY created_at ASC LIMIT 1)
    ),
    TRUE,
    '{}'::jsonb,
    TRUE,
    0
WHERE NOT EXISTS (SELECT 1 FROM dashboard WHERE is_system_default = TRUE);

-- 3. 为系统默认仪表盘添加初始 Widget（Quick Notes 欢迎信息）
INSERT INTO dashboard_widget (dashboard_id, widget_type, title, config, position_x, position_y, width, height, sort_order)
SELECT
    d.id,
    'note',
    '欢迎使用 TrackFlow',
    '{"content":"<h3>👋 欢迎使用 TrackFlow 项目管理系统</h3><p>这是您的起始仪表盘，管理员可以根据团队需要自定义此页面的内容。</p><ul><li>📋 创建和跟踪工单</li><li>📊 查看项目报表</li><li>🏃 管理 Sprint 迭代</li><li>👥 团队协作</li></ul><p>点击左侧导航栏开始使用，或联系管理员了解更多。</p>"}'::text,
    0, 0, 6, 4, 0
FROM dashboard d
WHERE d.is_system_default = TRUE;

-- 4. 添加一个 Issue List Widget（显示未解决工单）
INSERT INTO dashboard_widget (dashboard_id, widget_type, title, config, position_x, position_y, width, height, sort_order)
SELECT
    d.id,
    'issue_list',
    '待处理工单',
    '{"queryType":"open","label":"所有未关闭的工单"}'::text,
    6, 0, 6, 4, 1
FROM dashboard d
WHERE d.is_system_default = TRUE;

-- 5. 添加一个数字卡片 Widget（统计待处理工单数）
INSERT INTO dashboard_widget (dashboard_id, widget_type, title, config, position_x, position_y, width, height, sort_order)
SELECT
    d.id,
    'number_card',
    '待处理',
    '{"queryType":"open","label":"待处理工单数"}'::text,
    0, 4, 3, 2, 2
FROM dashboard d
WHERE d.is_system_default = TRUE;

-- 6. 添加一个数字卡片 Widget（统计已完成工单数）
INSERT INTO dashboard_widget (dashboard_id, widget_type, title, config, position_x, position_y, width, height, sort_order)
SELECT
    d.id,
    'number_card',
    '已完成',
    '{"queryType":"closed","label":"已完成工单数"}'::text,
    3, 4, 3, 2, 3
FROM dashboard d
WHERE d.is_system_default = TRUE;

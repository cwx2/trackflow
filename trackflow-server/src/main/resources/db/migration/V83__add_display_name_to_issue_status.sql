-- V37: 为 issue_status 表添加 display_name 列，用于存储中文显示名称
-- 通知消息、报表等后端生成的文本需要使用中文状态名

ALTER TABLE issue_status ADD COLUMN display_name VARCHAR(100);

-- 为现有状态填充中文显示名
UPDATE issue_status SET display_name = CASE name
    WHEN 'Open' THEN '待处理'
    WHEN 'In Progress' THEN '进行中'
    WHEN 'Code Review' THEN '代码审查'
    WHEN 'Testing' THEN '测试中'
    WHEN 'Done' THEN '已完成'
    WHEN 'Cancelled' THEN '已取消'
    WHEN 'Reopened' THEN '重新打开'
    WHEN 'Todo' THEN '待办'
    WHEN 'UI Todo' THEN 'UI 待办'
    WHEN 'Done (Local Env)' THEN '本地完成'
    WHEN 'No Test' THEN '无需测试'
    WHEN 'Pending Code Review' THEN '等待审查'
    WHEN 'Pending Publish' THEN '等待发布'
    WHEN 'Online' THEN '已上线'
    WHEN 'Solved' THEN '已解决'
    WHEN 'Closed' THEN '已关闭'
    WHEN 'Pending Cancel' THEN '待取消'
    WHEN 'Pending Extension' THEN '待延期'
    ELSE name
END;

-- 新创建的状态 display_name 默认与 name 相同（后续可单独修改）
COMMENT ON COLUMN issue_status.display_name IS '中文显示名称，用于通知/报表等后端生成的文本';

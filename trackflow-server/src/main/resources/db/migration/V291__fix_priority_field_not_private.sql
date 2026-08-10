-- V291: 修正 Priority 字段的 is_private 标记
-- 
-- 问题：V249 将 Priority 设为 is_private=true，导致所有非管理员角色
-- （产品经理、开发、测试等）创建或编辑工单时无法设置优先级。
--
-- 修正依据：YouTrack 文档 (private-and-public-issue-fields.html.md) 明确指出
-- Priority 是公共字段（对客户可见），只有 State/Type/Assignee/Fix version 才设为私有。
-- 所有工作角色都应能自由设置 Priority。
--
-- 修复方案：将 Priority 的 is_private 改为 false，与 YouTrack 行为一致。

UPDATE custom_field_definition
SET is_private = false, updated_at = NOW()
WHERE id = 1000000000000000001 AND name = 'Priority';

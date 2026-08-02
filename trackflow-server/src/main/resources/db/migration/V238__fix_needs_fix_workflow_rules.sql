-- V238__fix_needs_fix_workflow_rules.sql
-- 修复 V237 遗留的问题：
-- 1. 删除观察者(role_id=5)意外获得的待修复状态变更权限（安全问题）
-- 2. 为所有现有项目添加待修复状态的出口规则
-- 关联需求：REQ-47
--
-- 角色 ID 参考：
-- 2 = 项目管理员, 3 = 开发人员, 4 = 测试人员, 5 = 观察者, 6 = 产品经理, 7 = 技术负责人
-- 状态 ID 参考：
-- 2 = 进行中, 4 = 测试中, 6 = 已取消, 19 = 待修复

-- =====================================================
-- 1. 安全修复：删除观察者对"待修复"状态的所有转换规则
-- =====================================================
-- 观察者(role_id=5)不应有任何状态变更权限
DELETE FROM workflow_transition 
WHERE role_id = 5 
  AND (old_status_id = 19 OR new_status_id = 19);

-- =====================================================
-- 2. 为所有现有项目添加待修复状态的出口规则
-- =====================================================
-- 使用 ON CONFLICT (columns) DO NOTHING 保证幂等
-- 从现有项目级规则中获取每个项目的 workflow_definition_id

-- 创建临时表存储项目->workflow_definition_id 映射
CREATE TEMP TABLE project_workflow_map AS
SELECT DISTINCT project_id, workflow_definition_id
FROM workflow_transition
WHERE project_id IS NOT NULL AND workflow_definition_id IS NOT NULL;

-- 开发人员(3)：待修复 → 测试中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 3, 19, 4, false, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 开发人员(3)：待修复 → 进行中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 3, 19, 2, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 开发人员(3)：待修复 → 已取消
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 3, 19, 6, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 技术负责人(7)：待修复 → 测试中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 7, 19, 4, false, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 技术负责人(7)：待修复 → 进行中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 7, 19, 2, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 技术负责人(7)：待修复 → 已取消
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 7, 19, 6, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 项目管理员(2)：待修复 → 测试中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 2, 19, 4, false, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 项目管理员(2)：待修复 → 进行中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 2, 19, 2, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 项目管理员(2)：待修复 → 已取消
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 2, 19, 6, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 产品经理(6)：待修复 → 测试中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 6, 19, 4, false, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 产品经理(6)：待修复 → 进行中
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 6, 19, 2, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 产品经理(6)：待修复 → 已取消
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 6, 19, 6, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 测试人员(4)：待修复 → 测试中（复测）
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 4, 19, 4, false, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 测试人员(4)：待修复 → 进行中（恢复开发，需说明原因）
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 4, 19, 2, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 测试人员(4)：待修复 → 已取消
INSERT INTO workflow_transition (workflow_definition_id, project_id, issue_type, role_id, old_status_id, new_status_id, require_comment, author, assignee)
SELECT pwm.workflow_definition_id, pwm.project_id, '*', 4, 19, 6, true, false, false
FROM project_workflow_map pwm
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

-- 清理临时表
DROP TABLE IF EXISTS project_workflow_map;

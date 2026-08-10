-- V288: 为产品经理角色补充 project:view 权限
-- 产品经理（role_id=6）缺少 project:view 权限，导致无法查看项目成员列表，
-- 进而无法在 Sprint 规划/工单创建中选择负责人。
-- 其他所有项目角色（含观察者、非成员、匿名用户）均已拥有此权限。
-- REQ-476

INSERT INTO role_permission (role_id, permission)
VALUES (6, 'project:view')
ON CONFLICT DO NOTHING;

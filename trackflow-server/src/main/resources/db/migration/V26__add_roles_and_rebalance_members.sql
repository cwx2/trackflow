-- ============================================================
-- V26: 新增角色（产品经理、技术负责人）+ 补充测试用户 + 均衡分配成员
-- ============================================================

-- 1. 新增角色
INSERT INTO sys_role (id, name, code, description, role_type, builtin, sort_order, created_at, updated_at) VALUES
(6, '产品经理', 'product_manager', 'Issue 创建/编辑 + Sprint 管理 + 报表，不能管理成员/工作流', 'project', false, 6, NOW(), NOW()),
(7, '技术负责人', 'tech_lead', '开发人员权限 + 分配 Issue + 管理 Sprint', 'project', false, 7, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. 产品经理权限
INSERT INTO role_permission (role_id, permission) VALUES
(6, 'project:view'),
(6, 'issue:create'),
(6, 'issue:view'),
(6, 'issue:edit'),
(6, 'issue:change_status'),
(6, 'issue:comment'),
(6, 'issue:manage_attachments'),
(6, 'sprint:create'),
(6, 'sprint:edit'),
(6, 'sprint:view'),
(6, 'query:create'),
(6, 'query:share'),
(6, 'report:view'),
(6, 'report:create')
ON CONFLICT DO NOTHING;

-- 3. 技术负责人权限（开发人员 + assign + sprint 管理）
INSERT INTO role_permission (role_id, permission) VALUES
(7, 'project:view'),
(7, 'issue:create'),
(7, 'issue:view'),
(7, 'issue:edit'),
(7, 'issue:delete'),
(7, 'issue:assign'),
(7, 'issue:change_status'),
(7, 'issue:comment'),
(7, 'issue:manage_attachments'),
(7, 'sprint:create'),
(7, 'sprint:edit'),
(7, 'sprint:view'),
(7, 'query:create'),
(7, 'query:share'),
(7, 'report:view')
ON CONFLICT DO NOTHING;

-- 4. 新增测试用户
INSERT INTO sys_user (id, keycloak_id, username, display_name, email, status, created_at, updated_at) VALUES
(1009, 'pending-sunlei', 'sunlei', '孙磊', 'sunlei@company.com', 'active', NOW(), NOW()),
(1010, 'pending-zhoujie', 'zhoujie', '周杰', 'zhoujie@company.com', 'active', NOW(), NOW()),
(1011, 'pending-wumin', 'wumin', '吴敏', 'wumin@company.com', 'active', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 5. 重新分配项目成员
DO $body$
DECLARE
  v_de4_id BIGINT;
  v_fe1_id BIGINT;
BEGIN
  SELECT id INTO v_de4_id FROM project WHERE key = 'DE4';
  SELECT id INTO v_fe1_id FROM project WHERE key = 'FE1';

  -- 清除 DE4 现有成员
  DELETE FROM project_member WHERE project_id = v_de4_id;

  -- DE4 项目：12人，覆盖所有 7 个角色
  INSERT INTO project_member (project_id, user_id, role_id, joined_at) VALUES
    -- 项目管理员 (2人)
    (v_de4_id, 2074817502681837570, 2, NOW()),  -- testuser
    (v_de4_id, 1002, 2, NOW()),                  -- lina
    -- 技术负责人 (2人)
    (v_de4_id, 1001, 7, NOW()),                  -- zhangwei
    (v_de4_id, 1010, 7, NOW()),                  -- zhoujie
    -- 开发人员 (2人)
    (v_de4_id, 1003, 3, NOW()),                  -- wangqiang
    (v_de4_id, 1005, 3, NOW()),                  -- liuyang
    -- 产品经理 (2人)
    (v_de4_id, 1009, 6, NOW()),                  -- sunlei
    (v_de4_id, 1007, 6, NOW()),                  -- yangmin
    -- 测试人员 (2人)
    (v_de4_id, 1004, 4, NOW()),                  -- zhaojing
    (v_de4_id, 1006, 4, NOW()),                  -- chenfei
    -- 观察者 (2人)
    (v_de4_id, 1008, 5, NOW()),                  -- huanglei
    (v_de4_id, 1011, 5, NOW())                   -- wumin
  ON CONFLICT (project_id, user_id) DO NOTHING;

  -- 清除 FE1 现有成员
  DELETE FROM project_member WHERE project_id = v_fe1_id;

  -- FE1 项目：7人
  INSERT INTO project_member (project_id, user_id, role_id, joined_at) VALUES
    (v_fe1_id, 2074817502681837570, 2, NOW()),  -- testuser - 项目管理员
    (v_fe1_id, 1005, 7, NOW()),                  -- liuyang - 技术负责人
    (v_fe1_id, 1001, 3, NOW()),                  -- zhangwei - 开发人员
    (v_fe1_id, 1003, 3, NOW()),                  -- wangqiang - 开发人员
    (v_fe1_id, 1009, 6, NOW()),                  -- sunlei - 产品经理
    (v_fe1_id, 1004, 4, NOW()),                  -- zhaojing - 测试人员
    (v_fe1_id, 1008, 5, NOW())                   -- huanglei - 观察者
  ON CONFLICT (project_id, user_id) DO NOTHING;
END $body$;

-- 6. 工作流转换规则
-- 产品经理：全状态互转（同项目管理员）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id) VALUES
(NULL, '*', 6, 1, 2), (NULL, '*', 6, 1, 6),
(NULL, '*', 6, 2, 3), (NULL, '*', 6, 2, 4), (NULL, '*', 6, 2, 6),
(NULL, '*', 6, 3, 2), (NULL, '*', 6, 3, 4), (NULL, '*', 6, 3, 6),
(NULL, '*', 6, 4, 2), (NULL, '*', 6, 4, 5), (NULL, '*', 6, 4, 6),
(NULL, '*', 6, 5, 7), (NULL, '*', 6, 6, 7),
(NULL, '*', 6, 7, 2), (NULL, '*', 6, 7, 6)
ON CONFLICT DO NOTHING;

-- 技术负责人：同开发人员
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id) VALUES
(NULL, '*', 7, 1, 2), (NULL, '*', 7, 1, 6),
(NULL, '*', 7, 2, 3), (NULL, '*', 7, 2, 4), (NULL, '*', 7, 2, 6),
(NULL, '*', 7, 3, 2), (NULL, '*', 7, 3, 4),
(NULL, '*', 7, 7, 2)
ON CONFLICT DO NOTHING;

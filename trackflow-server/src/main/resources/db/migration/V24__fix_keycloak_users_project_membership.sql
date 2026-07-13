-- ============================================================
-- 修复 Keycloak 测试用户的项目成员关系
-- 问题：zhangwei/zhaojing/wangqiang/lina 通过 Keycloak 首次登录后，
-- UserSyncService 仅创建 sys_user 记录，不会自动添加到 project_member。
-- 导致这些用户没有项目级权限（如 sprint:view），访问 Sprint API 返回 403。
--
-- V22 曾尝试用硬编码 user_id (1001/1004) 修正，但那些是 V15 种子数据用户的 ID，
-- 不是 Keycloak 用户实际的 ID。本脚本通过 username 查找正确的 user_id。
-- ============================================================

DO $$
DECLARE
  v_project_id BIGINT;
  v_user_id BIGINT;
BEGIN
  -- 获取 DE4 项目 ID
  SELECT id INTO v_project_id FROM project WHERE key = 'DE4';
  IF v_project_id IS NULL THEN
    RAISE NOTICE 'Project DE4 not found, skipping';
    RETURN;
  END IF;

  -- testuser → project_admin (role_id=2)
  SELECT id INTO v_user_id FROM sys_user WHERE username = 'testuser';
  IF v_user_id IS NOT NULL THEN
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (v_project_id, v_user_id, 2, NOW())
    ON CONFLICT (project_id, user_id) DO UPDATE SET role_id = 2;
  END IF;

  -- lina → project_admin (role_id=2)
  SELECT id INTO v_user_id FROM sys_user WHERE username = 'lina';
  IF v_user_id IS NOT NULL THEN
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (v_project_id, v_user_id, 2, NOW())
    ON CONFLICT (project_id, user_id) DO UPDATE SET role_id = 2;
  END IF;

  -- zhangwei → developer (role_id=3)
  SELECT id INTO v_user_id FROM sys_user WHERE username = 'zhangwei';
  IF v_user_id IS NOT NULL THEN
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (v_project_id, v_user_id, 3, NOW())
    ON CONFLICT (project_id, user_id) DO UPDATE SET role_id = 3;
  END IF;

  -- wangqiang → developer (role_id=3)
  SELECT id INTO v_user_id FROM sys_user WHERE username = 'wangqiang';
  IF v_user_id IS NOT NULL THEN
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (v_project_id, v_user_id, 3, NOW())
    ON CONFLICT (project_id, user_id) DO UPDATE SET role_id = 3;
  END IF;

  -- zhaojing → tester (role_id=4)
  SELECT id INTO v_user_id FROM sys_user WHERE username = 'zhaojing';
  IF v_user_id IS NOT NULL THEN
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (v_project_id, v_user_id, 4, NOW())
    ON CONFLICT (project_id, user_id) DO UPDATE SET role_id = 4;
  END IF;

  RAISE NOTICE 'Keycloak users project membership fixed for project DE4 (id=%)', v_project_id;
END $$;

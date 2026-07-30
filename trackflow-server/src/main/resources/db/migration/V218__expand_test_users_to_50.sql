-- V218__expand_test_users_to_50.sql
-- 扩展测试用户到50人，建立三个项目的真实团队结构
--
-- 现有12人（testuser/lina/zhangwei/zhoujie/wangqiang/liuyang/sunlei/yangmin/zhaojing/chenfei/huanglei/wumin）
-- 新增38人，分布在三个项目：
--   DE4（后端开发）：原12人 + 新增8人 = 20人
--   FE1（前端开发）：原7人 + 新增11人 = 18人
--   MO1（移动端）：全新16人团队
--
-- role_id: 2=project_admin, 3=developer, 4=tester, 5=observer, 6=product_manager, 7=tech_lead

-- ============================================================
-- 第一步：插入38个新用户到 sys_user
-- keycloak_id 使用 'kc-{username}' 格式，首次登录时 UserSyncService 会用真实 UUID 覆盖
-- ============================================================
INSERT INTO sys_user (keycloak_id, username, display_name, email, status, created_at, updated_at)
VALUES
  -- DE4 新增成员（8人）
  ('kc-chengang',  'chengang',  '陈刚',   'chengang@company.com',  'active', NOW(), NOW()),
  ('kc-lixia',     'lixia',     '李霞',   'lixia@company.com',     'active', NOW(), NOW()),
  ('kc-wangfang',  'wangfang',  '王芳',   'wangfang@company.com',  'active', NOW(), NOW()),
  ('kc-zhangjie',  'zhangjie',  '张杰',   'zhangjie@company.com',  'active', NOW(), NOW()),
  ('kc-liwei',     'liwei',     '李威',   'liwei@company.com',     'active', NOW(), NOW()),
  ('kc-gaopeng',   'gaopeng',   '高鹏',   'gaopeng@company.com',   'active', NOW(), NOW()),
  ('kc-songna',    'songna',    '宋娜',   'songna@company.com',    'active', NOW(), NOW()),
  ('kc-xujun',     'xujun',     '徐俊',   'xujun@company.com',     'active', NOW(), NOW()),

  -- FE1 新增成员（11人）
  ('kc-panyu',     'panyu',     '潘宇',   'panyu@company.com',     'active', NOW(), NOW()),
  ('kc-dingchao',  'dingchao',  '丁超',   'dingchao@company.com',  'active', NOW(), NOW()),
  ('kc-helin',     'helin',     '何林',   'helin@company.com',     'active', NOW(), NOW()),
  ('kc-majing',    'majing',    '马晶',   'majing@company.com',    'active', NOW(), NOW()),
  ('kc-tanfei',    'tanfei',    '谭飞',   'tanfei@company.com',    'active', NOW(), NOW()),
  ('kc-jiangnan',  'jiangnan',  '江楠',   'jiangnan@company.com',  'active', NOW(), NOW()),
  ('kc-caiyun',    'caiyun',    '蔡云',   'caiyun@company.com',    'active', NOW(), NOW()),
  ('kc-luoming',   'luoming',   '罗明',   'luoming@company.com',   'active', NOW(), NOW()),
  ('kc-fenghao',   'fenghao',   '冯浩',   'fenghao@company.com',   'active', NOW(), NOW()),
  ('kc-shixin',    'shixin',    '施鑫',   'shixin@company.com',    'active', NOW(), NOW()),
  ('kc-niurui',    'niurui',    '牛蕊',   'niurui@company.com',    'active', NOW(), NOW()),

  -- MO1 移动端新团队（19人）
  ('kc-linbo',     'linbo',     '林波',   'linbo@company.com',     'active', NOW(), NOW()),
  ('kc-tangwei',   'tangwei',   '唐维',   'tangwei@company.com',   'active', NOW(), NOW()),
  ('kc-yuxin',     'yuxin',     '余欣',   'yuxin@company.com',     'active', NOW(), NOW()),
  ('kc-qiubo',     'qiubo',     '邱博',   'qiubo@company.com',     'active', NOW(), NOW()),
  ('kc-guoyan',    'guoyan',    '郭燕',   'guoyan@company.com',    'active', NOW(), NOW()),
  ('kc-huangfei',  'huangfei',  '黄飞',   'huangfei@company.com',  'active', NOW(), NOW()),
  ('kc-zenglong',  'zenglong',  '曾龙',   'zenglong@company.com',  'active', NOW(), NOW()),
  ('kc-peixuan',   'peixuan',   '裴璇',   'peixuan@company.com',   'active', NOW(), NOW()),
  ('kc-wugang',    'wugang',    '吴刚',   'wugang@company.com',    'active', NOW(), NOW()),
  ('kc-shaoli',    'shaoli',    '邵莉',   'shaoli@company.com',    'active', NOW(), NOW()),
  ('kc-kongjian',  'kongjian',  '孔健',   'kongjian@company.com',  'active', NOW(), NOW()),
  ('kc-miaomiao',  'miaomiao',  '苗淼',   'miaomiao@company.com',  'active', NOW(), NOW()),
  ('kc-bailong',   'bailong',   '白龙',   'bailong@company.com',   'active', NOW(), NOW()),
  ('kc-xionghui',  'xionghui',  '熊辉',   'xionghui@company.com',  'active', NOW(), NOW()),
  ('kc-sunyue',    'sunyue',    '孙悦',   'sunyue@company.com',    'active', NOW(), NOW()),
  ('kc-mengqi',    'mengqi',    '孟琦',   'mengqi@company.com',    'active', NOW(), NOW()),
  ('kc-fanyong',   'fanyong',   '范勇',   'fanyong@company.com',   'active', NOW(), NOW()),
  ('kc-chengru',   'chengru',   '程茹',   'chengru@company.com',   'active', NOW(), NOW()),
  ('kc-gaoxin',    'gaoxin',    '高欣',   'gaoxin@company.com',    'active', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- ============================================================
-- 第二步：确保 FE1 和 MO1 项目存在
-- ============================================================
INSERT INTO project (name, key, description, status, issue_sequence, created_by, created_at, updated_at)
SELECT '前端开发', 'FE1', 'TrackFlow 前端 Vue3 项目', 'active', 1,
       (SELECT id FROM sys_user WHERE username = 'testuser' LIMIT 1), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM project WHERE key = 'FE1');

INSERT INTO project (name, key, description, status, issue_sequence, created_by, created_at, updated_at)
SELECT '移动端开发', 'MO1', 'TrackFlow 移动端 App（iOS/Android）', 'active', 1,
       (SELECT id FROM sys_user WHERE username = 'gaoxin' LIMIT 1), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM project WHERE key = 'MO1');

-- ============================================================
-- 第三步：分配项目成员
-- 使用 DO 块动态查 user_id，避免硬编码 ID
-- ============================================================
DO $$
DECLARE
  v_de4_id  BIGINT;
  v_fe1_id  BIGINT;
  v_mo1_id  BIGINT;
  v_uid     BIGINT;

  -- 辅助过程：按 username 查 user_id，若不存在则跳过
  PROCEDURE assign(p_project_id BIGINT, p_username TEXT, p_role_id INT) AS $$
  DECLARE
    v_uid BIGINT;
  BEGIN
    SELECT id INTO v_uid FROM sys_user WHERE username = p_username;
    IF v_uid IS NULL THEN
      RAISE NOTICE 'User % not found, skipping', p_username;
      RETURN;
    END IF;
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (p_project_id, v_uid, p_role_id, NOW())
    ON CONFLICT (project_id, user_id) DO UPDATE SET role_id = p_role_id;
  END;
  $$ LANGUAGE plpgsql;

BEGIN
  SELECT id INTO v_de4_id FROM project WHERE key = 'DE4';
  SELECT id INTO v_fe1_id FROM project WHERE key = 'FE1';
  SELECT id INTO v_mo1_id FROM project WHERE key = 'MO1';

  IF v_de4_id IS NULL THEN RAISE EXCEPTION 'Project DE4 not found'; END IF;
  IF v_fe1_id IS NULL THEN RAISE EXCEPTION 'Project FE1 not found'; END IF;
  IF v_mo1_id IS NULL THEN RAISE EXCEPTION 'Project MO1 not found'; END IF;

  -- ──────────────────────────────────────────
  -- DE4 后端开发（20人）
  -- 保持原有12人角色不变，新增8人
  -- ──────────────────────────────────────────
  -- 原有成员（幂等 upsert，确保角色正确）
  CALL assign(v_de4_id, 'testuser',  2); -- project_admin
  CALL assign(v_de4_id, 'lina',      2); -- project_admin
  CALL assign(v_de4_id, 'zhangwei',  7); -- tech_lead
  CALL assign(v_de4_id, 'zhoujie',   7); -- tech_lead
  CALL assign(v_de4_id, 'wangqiang', 3); -- developer
  CALL assign(v_de4_id, 'liuyang',   3); -- developer
  CALL assign(v_de4_id, 'sunlei',    6); -- product_manager
  CALL assign(v_de4_id, 'yangmin',   6); -- product_manager
  CALL assign(v_de4_id, 'zhaojing',  4); -- tester
  CALL assign(v_de4_id, 'chenfei',   4); -- tester
  CALL assign(v_de4_id, 'huanglei',  5); -- observer
  CALL assign(v_de4_id, 'wumin',     5); -- observer
  -- 新增8人
  CALL assign(v_de4_id, 'chengang',  3); -- developer
  CALL assign(v_de4_id, 'lixia',     3); -- developer
  CALL assign(v_de4_id, 'wangfang',  3); -- developer
  CALL assign(v_de4_id, 'zhangjie',  7); -- tech_lead（新增一个TL）
  CALL assign(v_de4_id, 'liwei',     4); -- tester
  CALL assign(v_de4_id, 'gaopeng',   4); -- tester
  CALL assign(v_de4_id, 'songna',    5); -- observer
  CALL assign(v_de4_id, 'xujun',     6); -- product_manager

  -- ──────────────────────────────────────────
  -- FE1 前端开发（18人）
  -- 原有7人 + 新增11人
  -- ──────────────────────────────────────────
  -- 原有成员
  CALL assign(v_fe1_id, 'testuser',  2); -- project_admin
  CALL assign(v_fe1_id, 'liuyang',   7); -- tech_lead
  CALL assign(v_fe1_id, 'zhangwei',  3); -- developer（注意：DE4是TL，FE1是DEV）
  CALL assign(v_fe1_id, 'wangqiang', 3); -- developer
  CALL assign(v_fe1_id, 'sunlei',    6); -- product_manager
  CALL assign(v_fe1_id, 'zhaojing',  4); -- tester
  CALL assign(v_fe1_id, 'huanglei',  5); -- observer
  -- 新增11人
  CALL assign(v_fe1_id, 'gaoxin',    2); -- project_admin（新项目管理员）
  CALL assign(v_fe1_id, 'panyu',     7); -- tech_lead
  CALL assign(v_fe1_id, 'dingchao',  3); -- developer
  CALL assign(v_fe1_id, 'helin',     3); -- developer
  CALL assign(v_fe1_id, 'majing',    3); -- developer
  CALL assign(v_fe1_id, 'tanfei',    3); -- developer
  CALL assign(v_fe1_id, 'jiangnan',  4); -- tester
  CALL assign(v_fe1_id, 'caiyun',    4); -- tester
  CALL assign(v_fe1_id, 'luoming',   6); -- product_manager
  CALL assign(v_fe1_id, 'fenghao',   5); -- observer
  CALL assign(v_fe1_id, 'shixin',    5); -- observer
  CALL assign(v_fe1_id, 'niurui',    5); -- observer

  -- ──────────────────────────────────────────
  -- MO1 移动端（19人，全新团队）
  -- ──────────────────────────────────────────
  CALL assign(v_mo1_id, 'gaoxin',    2); -- project_admin（跨项目管理员）
  CALL assign(v_mo1_id, 'linbo',     7); -- tech_lead
  CALL assign(v_mo1_id, 'tangwei',   7); -- tech_lead
  CALL assign(v_mo1_id, 'yuxin',     3); -- developer
  CALL assign(v_mo1_id, 'qiubo',     3); -- developer
  CALL assign(v_mo1_id, 'guoyan',    3); -- developer
  CALL assign(v_mo1_id, 'huangfei',  3); -- developer
  CALL assign(v_mo1_id, 'zenglong',  3); -- developer
  CALL assign(v_mo1_id, 'peixuan',   3); -- developer
  CALL assign(v_mo1_id, 'wugang',    3); -- developer
  CALL assign(v_mo1_id, 'shaoli',    6); -- product_manager
  CALL assign(v_mo1_id, 'kongjian',  6); -- product_manager
  CALL assign(v_mo1_id, 'miaomiao',  4); -- tester
  CALL assign(v_mo1_id, 'bailong',   4); -- tester
  CALL assign(v_mo1_id, 'xionghui',  4); -- tester
  CALL assign(v_mo1_id, 'sunyue',    4); -- tester
  CALL assign(v_mo1_id, 'mengqi',    5); -- observer
  CALL assign(v_mo1_id, 'fanyong',   5); -- observer
  CALL assign(v_mo1_id, 'chengru',   5); -- observer

  RAISE NOTICE 'Expanded to 50 users: DE4(20) + FE1(18) + MO1(19)';
END $$;

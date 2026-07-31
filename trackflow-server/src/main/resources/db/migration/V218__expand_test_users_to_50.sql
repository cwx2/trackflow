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
-- 第三步：分配项目成员（使用 DO 块动态查 user_id）
-- ============================================================
DO $$
DECLARE
  v_de4_id  BIGINT;
  v_fe1_id  BIGINT;
  v_mo1_id  BIGINT;
  v_uid     BIGINT;

  -- 待分配的成员列表：(username, project_key, role_id)
  member_list TEXT[][] := ARRAY[
    -- DE4 原有成员
    ARRAY['testuser',  'DE4', '2'],
    ARRAY['lina',      'DE4', '2'],
    ARRAY['zhangwei',  'DE4', '7'],
    ARRAY['zhoujie',   'DE4', '7'],
    ARRAY['wangqiang', 'DE4', '3'],
    ARRAY['liuyang',   'DE4', '3'],
    ARRAY['sunlei',    'DE4', '6'],
    ARRAY['yangmin',   'DE4', '6'],
    ARRAY['zhaojing',  'DE4', '4'],
    ARRAY['chenfei',   'DE4', '4'],
    ARRAY['huanglei',  'DE4', '5'],
    ARRAY['wumin',     'DE4', '5'],
    -- DE4 新增8人
    ARRAY['chengang',  'DE4', '3'],
    ARRAY['lixia',     'DE4', '3'],
    ARRAY['wangfang',  'DE4', '3'],
    ARRAY['zhangjie',  'DE4', '7'],
    ARRAY['liwei',     'DE4', '4'],
    ARRAY['gaopeng',   'DE4', '4'],
    ARRAY['songna',    'DE4', '5'],
    ARRAY['xujun',     'DE4', '6'],
    -- FE1 原有7人
    ARRAY['testuser',  'FE1', '2'],
    ARRAY['liuyang',   'FE1', '7'],
    ARRAY['zhangwei',  'FE1', '3'],
    ARRAY['wangqiang', 'FE1', '3'],
    ARRAY['sunlei',    'FE1', '6'],
    ARRAY['zhaojing',  'FE1', '4'],
    ARRAY['huanglei',  'FE1', '5'],
    -- FE1 新增11人
    ARRAY['gaoxin',    'FE1', '2'],
    ARRAY['panyu',     'FE1', '7'],
    ARRAY['dingchao',  'FE1', '3'],
    ARRAY['helin',     'FE1', '3'],
    ARRAY['majing',    'FE1', '3'],
    ARRAY['tanfei',    'FE1', '3'],
    ARRAY['jiangnan',  'FE1', '4'],
    ARRAY['caiyun',    'FE1', '4'],
    ARRAY['luoming',   'FE1', '6'],
    ARRAY['fenghao',   'FE1', '5'],
    ARRAY['shixin',    'FE1', '5'],
    ARRAY['niurui',    'FE1', '5'],
    -- MO1 全新19人团队
    ARRAY['gaoxin',    'MO1', '2'],
    ARRAY['linbo',     'MO1', '7'],
    ARRAY['tangwei',   'MO1', '7'],
    ARRAY['yuxin',     'MO1', '3'],
    ARRAY['qiubo',     'MO1', '3'],
    ARRAY['guoyan',    'MO1', '3'],
    ARRAY['huangfei',  'MO1', '3'],
    ARRAY['zenglong',  'MO1', '3'],
    ARRAY['peixuan',   'MO1', '3'],
    ARRAY['wugang',    'MO1', '3'],
    ARRAY['shaoli',    'MO1', '6'],
    ARRAY['kongjian',  'MO1', '6'],
    ARRAY['miaomiao',  'MO1', '4'],
    ARRAY['bailong',   'MO1', '4'],
    ARRAY['xionghui',  'MO1', '4'],
    ARRAY['sunyue',    'MO1', '4'],
    ARRAY['mengqi',    'MO1', '5'],
    ARRAY['fanyong',   'MO1', '5'],
    ARRAY['chengru',   'MO1', '5']
  ];
  rec TEXT[];
  v_project_id BIGINT;
BEGIN
  SELECT id INTO v_de4_id FROM project WHERE key = 'DE4';
  SELECT id INTO v_fe1_id FROM project WHERE key = 'FE1';
  SELECT id INTO v_mo1_id FROM project WHERE key = 'MO1';

  IF v_de4_id IS NULL THEN RAISE EXCEPTION 'Project DE4 not found'; END IF;
  IF v_fe1_id IS NULL THEN RAISE EXCEPTION 'Project FE1 not found'; END IF;
  IF v_mo1_id IS NULL THEN RAISE EXCEPTION 'Project MO1 not found'; END IF;

  FOREACH rec SLICE 1 IN ARRAY member_list LOOP
    -- 查找用户
    SELECT id INTO v_uid FROM sys_user WHERE username = rec[1];
    IF v_uid IS NULL THEN
      RAISE NOTICE 'User % not found, skipping', rec[1];
      CONTINUE;
    END IF;

    -- 确定项目 ID
    IF rec[2] = 'DE4' THEN
      v_project_id := v_de4_id;
    ELSIF rec[2] = 'FE1' THEN
      v_project_id := v_fe1_id;
    ELSE
      v_project_id := v_mo1_id;
    END IF;

    -- 插入项目成员（已存在则跳过，避免覆盖现有角色）
    INSERT INTO project_member (project_id, user_id, role_id, joined_at)
    VALUES (v_project_id, v_uid, rec[3]::INT, NOW())
    ON CONFLICT (project_id, user_id, role_id) DO NOTHING;
  END LOOP;

  RAISE NOTICE 'Expanded to 50 users: DE4(20) + FE1(18) + MO1(19)';
END $$;

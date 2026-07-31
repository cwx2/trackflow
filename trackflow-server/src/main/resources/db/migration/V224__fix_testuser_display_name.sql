-- V224__fix_testuser_display_name.sql
-- 修复 testuser 测试账号的显示名称，从 "Test User" 改为 "系统管理员"
-- 对应需求 REQ-963：活动历史中第三方账号显示名称为"Test User"而非真实姓名

UPDATE sys_user
SET display_name = '系统管理员'
WHERE username = 'testuser'
  AND display_name = 'Test User';

COMMENT ON COLUMN sys_user.display_name IS '用户显示名称，CJK 名为"姓+名"（无空格），西方名为"名 空格 姓"';

-- V266__user_profile_fields.sql
-- 为 sys_user 表新增个人资料偏好字段，支持用户编辑自己的时区/语言/日期格式等

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'Asia/Shanghai';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS language VARCHAR(10) DEFAULT 'zh-CN';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS date_format VARCHAR(20) DEFAULT 'yyyy-MM-dd';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS first_day_of_week VARCHAR(10) DEFAULT 'MONDAY';

COMMENT ON COLUMN sys_user.timezone IS '用户本地时区';
COMMENT ON COLUMN sys_user.language IS '界面语言偏好';
COMMENT ON COLUMN sys_user.date_format IS '日期格式偏好';
COMMENT ON COLUMN sys_user.first_day_of_week IS '每周第一天 (MONDAY/SUNDAY)';

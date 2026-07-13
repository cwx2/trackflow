-- 修复中文用户显示名称顺序问题
-- 原因：Keycloak 默认 name claim 按西方顺序 (firstName + " " + lastName) 拼接，
-- 中文用户 firstName=名、lastName=姓，导致存入 "名 姓" 格式（如 "伟 张"）。
-- 修复为正确的 "姓名" 连写格式（如 "张伟"）。

-- zhangwei: "伟 张" → "张伟"
UPDATE sys_user SET display_name = '张伟' WHERE username = 'zhangwei' AND display_name = '伟 张';

-- zhaojing: "静 赵" → "赵静"
UPDATE sys_user SET display_name = '赵静' WHERE username = 'zhaojing' AND display_name = '静 赵';

-- wangqiang: "王 强" → "王强" (顺序正确但有多余空格，也可能是 "强 王")
UPDATE sys_user SET display_name = '王强' WHERE username = 'wangqiang' AND (display_name = '强 王' OR display_name = '王 强');

-- huanglei: "磊 黄" → "黄磊"
UPDATE sys_user SET display_name = '黄磊' WHERE username = 'huanglei' AND display_name = '磊 黄';

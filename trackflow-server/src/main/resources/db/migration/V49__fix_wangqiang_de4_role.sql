-- ============================================================
-- V49: 修复 DE4 项目成员角色数据
--
-- 问题：wangqiang 在 DE4 项目中被错误分配为"观察者"（role_id=5），
--       应为"开发人员"（role_id=3）。
--       原因是 V26 执行后通过 UI 操作错误修改了角色。
--
-- 修复：将 wangqiang 在 DE4 中的角色更正为开发人员。
-- ============================================================

UPDATE project_member
SET role_id = 3
WHERE user_id = 1003
  AND project_id = (SELECT id FROM project WHERE key = 'DE4')
  AND role_id = 5;

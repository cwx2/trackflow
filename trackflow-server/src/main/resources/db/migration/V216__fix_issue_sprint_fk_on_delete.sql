-- V215: 修复 issue.sprint_id 外键约束，允许删除 Sprint 时自动清空引用
-- 
-- 问题：当存在软删除（回收站中）的工单引用某个 Sprint 时，删除该 Sprint 会触发 FK RESTRICT 约束违规。
-- 原因：SprintService.delete() 只处理未软删除的工单（MyBatis-Plus 逻辑删除过滤），遗漏了软删除工单的 FK 引用。
-- 
-- 方案：将 fk_issue_sprint 外键的删除动作从 RESTRICT（默认）改为 SET NULL。
--       这样当 Sprint 被删除时，引用它的所有工单（包括软删除的）的 sprint_id 自动置为 NULL。
-- 
-- 影响：
--   - 物理删除 Sprint 时，issue.sprint_id 自动置 NULL（数据库层面保证，无需 Service 层额外处理）
--   - 现有业务逻辑不受影响（SprintService 已经会处理未软删除工单的迁移）
--   - 这是防御性设计，即使 Service 层遗漏某些边界情况，数据库也能保证 FK 不会阻断操作
-- 
-- YouTrack 参考：删除 Sprint 后，所有关联引用必须被清除，包括已删除的工单。
-- OpenProject 参考：使用 dependent: :nullify 模式，删除 Version 时自动清空 work_package.version_id。
-- 
-- 注意：issue_sprint 关联表的 FK 已在 V209 中定义为 ON DELETE CASCADE，无需修改。

-- 删除旧的外键约束
ALTER TABLE issue DROP CONSTRAINT IF EXISTS fk_issue_sprint;

-- 重新创建外键约束，添加 ON DELETE SET NULL
ALTER TABLE issue ADD CONSTRAINT fk_issue_sprint 
    FOREIGN KEY (sprint_id) REFERENCES sprint(id) ON DELETE SET NULL;

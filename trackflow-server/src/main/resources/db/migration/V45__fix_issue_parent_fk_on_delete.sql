-- V45: 修改 issue.parent_id FK 约束，添加 ON DELETE SET NULL
-- 用途：当父工单被物理删除时，自动将子工单的 parent_id 置为 NULL，避免 FK 约束阻止删除
-- 同时也是软删除级联处理的兜底保障（Service 层会主动清除，此为数据库层防护）

ALTER TABLE issue DROP CONSTRAINT issue_parent_id_fkey;
ALTER TABLE issue ADD CONSTRAINT issue_parent_id_fkey
    FOREIGN KEY (parent_id) REFERENCES issue(id) ON DELETE SET NULL;

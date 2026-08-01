-- V230 已在部分开发数据库中执行过，之后 definition_snapshot 才加入旧迁移文件。
-- Flyway 不会重新执行已登记的版本，因此用新的前向迁移补齐该字段。

ALTER TABLE automation_execution
    ADD COLUMN IF NOT EXISTS definition_snapshot JSONB;

COMMENT ON COLUMN automation_execution.definition_snapshot
    IS '执行开始时冻结的工作流定义，恢复时不受草稿修改影响';

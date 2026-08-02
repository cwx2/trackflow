-- 将“发布版本”和“启用自动运行”拆分，发布后必须由用户显式启动。

ALTER TABLE automation_workflow
    ADD COLUMN IF NOT EXISTS runtime_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS activated_by BIGINT REFERENCES sys_user(id) ON DELETE SET NULL;

UPDATE automation_workflow
SET runtime_enabled = FALSE
WHERE runtime_enabled IS DISTINCT FROM FALSE;

CREATE INDEX IF NOT EXISTS idx_automation_workflow_active_trigger
    ON automation_workflow(trigger_type, project_id)
    WHERE status = 'published' AND runtime_enabled = TRUE;

COMMENT ON COLUMN automation_workflow.runtime_enabled IS '是否已由用户显式启动自动运行；发布本身不会启动';
COMMENT ON COLUMN automation_workflow.activated_at IS '最近一次人工启动时间';
COMMENT ON COLUMN automation_workflow.activated_by IS '最近一次人工启动用户';

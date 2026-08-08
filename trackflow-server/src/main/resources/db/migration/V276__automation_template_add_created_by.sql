-- 为自动化模板表增加 created_by 字段，支持用户自定义模板

ALTER TABLE automation_workflow_template ADD COLUMN created_by BIGINT;

COMMENT ON COLUMN automation_workflow_template.created_by IS '创建者用户ID（内置模板为 NULL）';

-- 增加索引便于按创建者查询自定义模板
CREATE INDEX idx_automation_workflow_template_created_by ON automation_workflow_template(created_by);

-- Sprint 名称在同一项目内必须唯一（大小写不敏感）
-- 参考 OpenProject: validates :name, uniqueness: { scope: [:project_id], case_sensitive: false }
CREATE UNIQUE INDEX uk_sprint_project_name ON sprint(project_id, LOWER(name));

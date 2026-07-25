-- V177__add_custom_field_sort_mode.sql
-- 为 custom_field_definition 表增加排序模式字段，支持选项集的自动排序。
-- 参考 YouTrack 的 "Sort Values in a Set" 功能：Manual / Sort by name / Sort by version

ALTER TABLE custom_field_definition ADD COLUMN sort_mode VARCHAR(20) NOT NULL DEFAULT 'manual';
COMMENT ON COLUMN custom_field_definition.sort_mode IS '选项排序模式: manual(手动) / name_asc(名称升序) / name_desc(名称降序) / name_ci_asc(名称不区分大小写升序) / name_ci_desc(名称不区分大小写降序)';

-- V270__add_build_group_to_field_format_check.sql
-- 扩展 custom_field_definition 的 field_format CHECK 约束，新增 'build' 和 'group' 两种类型

ALTER TABLE custom_field_definition DROP CONSTRAINT ck_custom_field_format;

ALTER TABLE custom_field_definition ADD CONSTRAINT ck_custom_field_format
  CHECK (field_format IN ('string','text','int','float','date','datetime','bool','list','user','period','state','ownedField','version','build','group'));

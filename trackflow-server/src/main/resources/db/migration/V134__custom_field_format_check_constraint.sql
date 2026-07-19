-- REQ-619: 给 custom_field_definition.field_format 添加 CHECK 约束
-- 限制 field_format 为受支持的枚举值，防止绕过 Service 层写入非法格式
ALTER TABLE custom_field_definition
    ADD CONSTRAINT ck_custom_field_format
        CHECK (field_format IN ('string', 'text', 'int', 'float', 'date', 'datetime', 'bool', 'list', 'user'));

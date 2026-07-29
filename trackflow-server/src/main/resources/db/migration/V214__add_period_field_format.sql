-- REQ-3: 添加 period（时间周期）字段类型支持
-- period 类型存储分钟数（整数），支持类似 YouTrack 的"估计工时"、"实际工时"等字段

-- 删除旧约束
ALTER TABLE custom_field_definition
    DROP CONSTRAINT IF EXISTS ck_custom_field_format;

-- 重建约束，新增 period 类型
ALTER TABLE custom_field_definition
    ADD CONSTRAINT ck_custom_field_format
        CHECK (field_format IN ('string', 'text', 'int', 'float', 'date', 'datetime', 'bool', 'list', 'user', 'period'));

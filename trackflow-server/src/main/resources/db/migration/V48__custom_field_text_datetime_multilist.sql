-- ============================================================
-- 扩展自定义字段类型：text(多行富文本), datetime(日期+时间), list 多值模式
-- ============================================================

-- 添加 is_multi 列，标识 list 类型是否支持多值选择
ALTER TABLE custom_field_definition
    ADD COLUMN is_multi BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN custom_field_definition.is_multi IS '列表类型是否支持多值选择（仅 list 类型有效）';

-- 更新字段类型注释
COMMENT ON COLUMN custom_field_definition.field_format IS '字段类型: string, text, int, float, date, datetime, bool, list, user';

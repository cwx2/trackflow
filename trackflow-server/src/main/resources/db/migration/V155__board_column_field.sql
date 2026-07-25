-- V155__board_column_field.sql
-- 为看板支持"任意字段作为列标识"功能，参照 YouTrack Board Settings > Columns 的设计。
-- 新增 column_field 字段到 board_general_config 表，表示当前看板使用哪个字段来标识列。
-- 默认值为 'status'，保持向后兼容。

ALTER TABLE board_general_config
    ADD COLUMN column_field VARCHAR(64) NOT NULL DEFAULT 'status';

COMMENT ON COLUMN board_general_config.column_field IS '看板列标识字段：status=按状态分列(默认), priority=按优先级分列, 未来可扩展 custom_field_id';

-- 为 board_column_config 新增 field_value 字段，存储通用字段值标识。
-- 当 column_field='status' 时使用 status_id（向后兼容）；
-- 当 column_field='priority' 时使用 priority 值字符串。
ALTER TABLE board_column_config
    ADD COLUMN field_value VARCHAR(100);

COMMENT ON COLUMN board_column_config.field_value IS '通用列标识值。status模式=status_id字符串, priority模式=priority枚举值(Critical/High/Normal/Low)';

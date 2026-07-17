-- 为自定义字段选项添加颜色支持
-- 参考 YouTrack 的 Value-specific Color 设置
ALTER TABLE custom_field_option ADD COLUMN IF NOT EXISTS color VARCHAR(20);

COMMENT ON COLUMN custom_field_option.color IS '选项颜色值（HEX 格式如 #4CAF50），为 NULL 时表示无颜色配置';

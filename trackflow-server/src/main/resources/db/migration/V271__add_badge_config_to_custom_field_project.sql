-- V271: 为 custom_field_project 增加数字徽章配置
-- 支持在工单列表标题左侧以数字徽章形式展示整数类型自定义字段的值（对标 YouTrack 数字优先级效果）

ALTER TABLE custom_field_project ADD COLUMN show_as_badge BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE custom_field_project ADD COLUMN badge_color_rules JSONB;

COMMENT ON COLUMN custom_field_project.show_as_badge IS '是否在工单列表标题左侧以数字徽章形式展示该字段值（仅整数字段有效）';
COMMENT ON COLUMN custom_field_project.badge_color_rules IS '徽章颜色规则 JSON 数组，格式: [{"max":1,"color":"#ef4444"},{"max":3,"color":"#f97316"},{"color":"#3b82f6"}]';

-- =============================================================================
-- V65: 系统设置表 - 存储全局配置项（键值对模式）
-- =============================================================================
-- 参考 OpenProject Setting 模型，使用 key-value 存储系统级配置。
-- 初始种子：时间追踪配置（hours_per_day, working_days）
-- =============================================================================

CREATE TABLE system_setting (
    id          BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,   -- 配置键，如 'time_tracking.hours_per_day'
    value       TEXT NOT NULL,                  -- 配置值（JSON 或纯文本）
    description VARCHAR(500),                   -- 配置项描述
    category    VARCHAR(50) NOT NULL DEFAULT 'general',  -- 分类
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_system_setting_category ON system_setting(category);

COMMENT ON TABLE system_setting IS '系统级全局配置表';
COMMENT ON COLUMN system_setting.setting_key IS '唯一配置键，格式：category.name';
COMMENT ON COLUMN system_setting.value IS '配置值，复杂类型用 JSON 存储';

-- 种子数据：时间追踪默认配置
INSERT INTO system_setting (setting_key, value, description, category) VALUES
    ('time_tracking.hours_per_day', '8', '每日工作小时数，用于工时单位换算（1d = Xh）', 'time_tracking'),
    ('time_tracking.working_days', '[1,2,3,4,5]', '每周工作日（1=周一，7=周日），JSON 数组', 'time_tracking')
ON CONFLICT (setting_key) DO NOTHING;

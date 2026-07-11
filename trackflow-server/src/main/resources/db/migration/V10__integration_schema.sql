-- ============================================================
-- Webhook + 通知表
-- ============================================================

-- Webhook 配置
CREATE TABLE webhook (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    secret VARCHAR(200),
    events JSONB NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Webhook 调用日志
CREATE TABLE webhook_log (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES webhook(id) ON DELETE CASCADE,
    event VARCHAR(100) NOT NULL,
    payload JSONB,
    response_status INT,
    response_body TEXT,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wh_log_webhook ON webhook_log(webhook_id);
CREATE INDEX idx_wh_log_created ON webhook_log(created_at);

-- 通知
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    content TEXT,
    type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_user_unread ON notification(user_id, is_read);
CREATE INDEX idx_notif_created ON notification(created_at);

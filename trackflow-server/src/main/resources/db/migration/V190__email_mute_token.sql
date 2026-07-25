-- V190__email_mute_token.sql
-- 为邮件通知中的"一键静音此工单"功能创建 token 存储表。
-- token 基于 UUID，有效期 30 天，使用后不删除（支持重复点击幂等）。

CREATE TABLE IF NOT EXISTS notification_email_mute_token (
    id           BIGSERIAL PRIMARY KEY,
    token        VARCHAR(64)  NOT NULL UNIQUE,
    user_id      BIGINT       NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    resource_type VARCHAR(64) NOT NULL,
    resource_id  BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP    NOT NULL,
    used_at      TIMESTAMP    NULL
);

COMMENT ON TABLE notification_email_mute_token IS '邮件静音 token，通过邮件底部链接一键静音工单通知';
COMMENT ON COLUMN notification_email_mute_token.token IS '安全随机 token（UUID v4），嵌入邮件链接中';
COMMENT ON COLUMN notification_email_mute_token.expires_at IS 'token 有效期（默认 30 天）';
COMMENT ON COLUMN notification_email_mute_token.used_at IS '首次使用时间，NULL 表示未使用';

CREATE INDEX idx_notification_email_mute_token_token ON notification_email_mute_token(token);
CREATE INDEX idx_notification_email_mute_token_user_id ON notification_email_mute_token(user_id);

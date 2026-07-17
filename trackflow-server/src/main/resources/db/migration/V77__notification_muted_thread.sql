-- 通知线程静音表：存储用户对特定资源的静音关系
CREATE TABLE notification_muted_thread (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id),
    resource_type VARCHAR(50) NOT NULL DEFAULT 'issue',
    resource_id BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_muted_thread_user_resource UNIQUE (user_id, resource_type, resource_id)
);

-- 查询索引：按用户快速查找所有静音资源
CREATE INDEX idx_muted_thread_user_id ON notification_muted_thread(user_id);

COMMENT ON TABLE notification_muted_thread IS '通知线程静音：用户对特定工单/资源的静音关系';
COMMENT ON COLUMN notification_muted_thread.user_id IS '用户ID';
COMMENT ON COLUMN notification_muted_thread.resource_type IS '资源类型（issue/project 等）';
COMMENT ON COLUMN notification_muted_thread.resource_id IS '资源ID（如 issue_id）';

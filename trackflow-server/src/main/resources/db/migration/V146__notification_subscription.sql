-- 通知订阅表：用户可基于标签或保存搜索创建订阅规则
-- 对标 YouTrack Notifications → Subscriptions
CREATE TABLE notification_subscription (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES sys_user(id),
    -- 订阅名称（用于显示），默认订阅由系统生成
    name            VARCHAR(200) NOT NULL,
    -- 订阅来源类型：tag / saved_query / builtin
    source_type     VARCHAR(20)  NOT NULL,
    -- 来源 ID：tag_id 或 saved_query_id，builtin 类型为 NULL
    source_id       BIGINT       NULL,
    -- 内建订阅标识：assigned_to_me / reported_by_me / commented_by_me
    builtin_key     VARCHAR(50)  NULL,
    -- 是否为系统默认订阅（不可删除，只能禁用事件）
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 触发事件开关（JSONB 存储灵活的事件配置）
    -- 格式：{"onCreated":true,"onUpdated":true,"onResolved":true,"onCommented":true,"onTagAdded":true,"onTagRemoved":true}
    events          JSONB        NOT NULL DEFAULT '{"onCreated":true,"onUpdated":true,"onResolved":true,"onCommented":true}'::jsonb,

    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

-- 用户 + 来源唯一约束（防止同一用户对同一来源重复订阅）
CREATE UNIQUE INDEX uk_subscription_user_source
    ON notification_subscription(user_id, source_type, COALESCE(source_id, 0), COALESCE(builtin_key, ''));

-- 查询用户所有订阅
CREATE INDEX idx_subscription_user_id ON notification_subscription(user_id);

-- 查询特定标签的订阅者（通知匹配用）
CREATE INDEX idx_subscription_tag ON notification_subscription(source_type, source_id)
    WHERE source_type = 'tag';

-- 查询特定保存搜索的订阅者（通知匹配用）
CREATE INDEX idx_subscription_saved_query ON notification_subscription(source_type, source_id)
    WHERE source_type = 'saved_query';

COMMENT ON TABLE notification_subscription IS '通知订阅规则：用户基于标签或保存搜索创建的通知订阅';
COMMENT ON COLUMN notification_subscription.source_type IS '订阅来源类型：tag=标签, saved_query=保存搜索, builtin=内建默认';
COMMENT ON COLUMN notification_subscription.builtin_key IS '内建订阅标识：assigned_to_me, reported_by_me, commented_by_me';
COMMENT ON COLUMN notification_subscription.events IS '触发事件配置 JSON：onCreated/onUpdated/onResolved/onCommented/onTagAdded/onTagRemoved';

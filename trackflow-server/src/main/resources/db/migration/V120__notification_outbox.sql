-- ============================================================
-- V120: 通知发件箱（Outbox）表
-- 用于存储异步通知发送失败后的待重试记录
-- 参考 OpenProject 的 ActiveJob + GoodJob 死信队列机制
-- ============================================================

CREATE TABLE notification_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,           -- 事件类型（如 issue_assigned, sprint_started）
    payload         JSONB NOT NULL,                 -- 完整事件上下文（序列化的通知参数）
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending / failed / completed
    retry_count     INT NOT NULL DEFAULT 0,
    max_retries     INT NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP,                      -- 下次重试时间（指数退避计算）
    error_message   TEXT,                           -- 最后一次失败的错误信息
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP,
    CONSTRAINT chk_outbox_status CHECK (status IN ('pending', 'failed', 'completed'))
);

-- 索引：定时任务按 status + next_retry_at 查询待重试记录
CREATE INDEX idx_notification_outbox_retry
    ON notification_outbox (status, next_retry_at)
    WHERE status = 'pending';

-- 索引：管理员查看失败记录
CREATE INDEX idx_notification_outbox_failed
    ON notification_outbox (status, created_at DESC)
    WHERE status = 'failed';

COMMENT ON TABLE notification_outbox IS '通知发件箱：存储发送失败的通知事件，供定时任务重试或管理员手动重试';
COMMENT ON COLUMN notification_outbox.event_type IS '通知事件类型，对应 NotificationHelper 方法名（如 notifyAssigned）';
COMMENT ON COLUMN notification_outbox.payload IS '完整事件参数 JSON，包含重新执行通知所需的全部数据';
COMMENT ON COLUMN notification_outbox.status IS '状态：pending=待重试, failed=超过最大重试次数, completed=重试成功';
COMMENT ON COLUMN notification_outbox.next_retry_at IS '下次重试时间，由指数退避策略计算（1min, 5min, 30min, 2h, 12h）';

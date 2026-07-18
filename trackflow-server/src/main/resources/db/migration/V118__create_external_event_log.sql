-- V30: Create external_event_log table for third-party integration event tracking
-- Supports outbound/inbound event logging, retry mechanism, and audit trail
-- Reference: OpenProject Webhooks::Log model

CREATE TABLE external_event_log (
    id BIGSERIAL PRIMARY KEY,
    adapter_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('outbound', 'inbound')),
    reference_id VARCHAR(100),
    payload JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processing', 'success', 'failed', 'cancelled')),
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Performance indexes
CREATE INDEX idx_external_event_log_adapter_type ON external_event_log(adapter_type);
CREATE INDEX idx_external_event_log_event_type ON external_event_log(event_type);
CREATE INDEX idx_external_event_log_status ON external_event_log(status);
CREATE INDEX idx_external_event_log_reference_id ON external_event_log(reference_id);
CREATE INDEX idx_external_event_log_created_at ON external_event_log(created_at DESC);
-- Retry polling index: find failed events that are ready to retry
CREATE INDEX idx_external_event_log_retry ON external_event_log(status, next_retry_at)
    WHERE status = 'failed' AND next_retry_at IS NOT NULL;

COMMENT ON TABLE external_event_log IS '第三方集成事件日志表，记录所有出站/入站集成事件';
COMMENT ON COLUMN external_event_log.adapter_type IS '适配器类型（email/sug/webhook/migration）';
COMMENT ON COLUMN external_event_log.event_type IS '事件类型（issue.created/issue.status_changed 等）';
COMMENT ON COLUMN external_event_log.direction IS '方向：outbound=向外推送，inbound=外部接收';
COMMENT ON COLUMN external_event_log.reference_id IS '关联的业务实体标识（如 issueKey）';
COMMENT ON COLUMN external_event_log.payload IS '事件载荷 JSON';
COMMENT ON COLUMN external_event_log.status IS '处理状态：pending/processing/success/failed/cancelled';
COMMENT ON COLUMN external_event_log.error_message IS '失败时的错误消息';
COMMENT ON COLUMN external_event_log.retry_count IS '已重试次数';
COMMENT ON COLUMN external_event_log.next_retry_at IS '下次重试时间（指数退避）';

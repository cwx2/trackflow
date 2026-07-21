-- V158__add_issue_key_history_table.sql
-- 工单移动后记录旧 Issue Key 的历史表，支持旧 Key 重定向到新 Key

CREATE TABLE issue_key_history (
    id          BIGSERIAL PRIMARY KEY,
    issue_id    BIGINT NOT NULL REFERENCES issue(id),
    old_key     VARCHAR(30) NOT NULL,
    new_key     VARCHAR(30) NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT now(),
    changed_by  BIGINT NOT NULL
);

COMMENT ON TABLE issue_key_history IS '工单 Key 变更历史，用于旧 Key 重定向';
COMMENT ON COLUMN issue_key_history.old_key IS '变更前的 Issue Key';
COMMENT ON COLUMN issue_key_history.new_key IS '变更后的 Issue Key';
COMMENT ON COLUMN issue_key_history.changed_by IS '执行移动操作的用户 ID';

-- 旧 Key 唯一索引，确保通过旧 Key 能快速定位工单
CREATE UNIQUE INDEX idx_issue_key_history_old_key ON issue_key_history(old_key);

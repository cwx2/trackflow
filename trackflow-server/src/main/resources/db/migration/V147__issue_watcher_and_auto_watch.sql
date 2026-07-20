-- ===========================================================================
-- V146: Issue Watcher 表 + 自动关注配置字段
-- REQ-703: 通知系统自动关注（Auto-Star）行为配置
-- REQ-321: Watcher 基础机制（前置依赖同步实现）
-- ===========================================================================

-- 1. 创建 issue_watcher 表
CREATE TABLE issue_watcher (
    id          BIGSERIAL PRIMARY KEY,
    issue_id    BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_issue_watcher UNIQUE(issue_id, user_id)
);

CREATE INDEX idx_watcher_user ON issue_watcher(user_id);
CREATE INDEX idx_watcher_issue ON issue_watcher(issue_id);

COMMENT ON TABLE issue_watcher IS '工单关注（Watcher）关系表';
COMMENT ON COLUMN issue_watcher.issue_id IS '被关注的工单ID';
COMMENT ON COLUMN issue_watcher.user_id IS '关注者用户ID';

-- 2. 在 notification_preference 表增加 watched 通知开关
ALTER TABLE notification_preference
    ADD COLUMN IF NOT EXISTS on_watched_updated BOOLEAN DEFAULT true;

COMMENT ON COLUMN notification_preference.on_watched_updated IS '我关注的工单有更新时通知我';

-- 3. 在 notification_preference 表增加自动关注行为配置字段
ALTER TABLE notification_preference
    ADD COLUMN IF NOT EXISTS auto_watch_on_create BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS auto_watch_on_comment BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS auto_watch_on_update BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS auto_watch_on_assign BOOLEAN DEFAULT true;

COMMENT ON COLUMN notification_preference.auto_watch_on_create IS '创建工单时自动关注';
COMMENT ON COLUMN notification_preference.auto_watch_on_comment IS '评论工单时自动关注';
COMMENT ON COLUMN notification_preference.auto_watch_on_update IS '修改工单时自动关注';
COMMENT ON COLUMN notification_preference.auto_watch_on_assign IS '被分配为负责人时自动关注';

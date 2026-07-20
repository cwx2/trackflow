-- 报表精细化共享表
-- 支持共享给用户或用户组，并区分权限级别（查看/编辑）
-- 与 dashboard_share 结构一致，统一共享框架
CREATE TABLE report_share (
    id          BIGSERIAL PRIMARY KEY,
    report_id   BIGINT NOT NULL REFERENCES report_definition(id) ON DELETE CASCADE,
    -- 共享对象类型：'user' 或 'group'
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('user', 'group')),
    -- 共享目标 ID（用户 ID 或用户组 ID）
    target_id   BIGINT NOT NULL,
    -- 权限级别：'view' 只读，'edit' 可编辑
    permission  VARCHAR(10) NOT NULL DEFAULT 'view' CHECK (permission IN ('view', 'edit')),
    created_by  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- 唯一约束：同一报表对同一目标只共享一次
CREATE UNIQUE INDEX idx_report_share_unique
    ON report_share(report_id, target_type, target_id);

-- 查询某用户被直接共享的报表
CREATE INDEX idx_report_share_user
    ON report_share(target_type, target_id) WHERE target_type = 'user';

-- 查询某报表的所有共享对象
CREATE INDEX idx_report_share_report
    ON report_share(report_id);

COMMENT ON TABLE report_share IS '报表精细化共享关联表';
COMMENT ON COLUMN report_share.target_type IS '共享目标类型：user=用户，group=用户组';
COMMENT ON COLUMN report_share.target_id IS '共享目标 ID';
COMMENT ON COLUMN report_share.permission IS '权限级别：view=只读，edit=可编辑';

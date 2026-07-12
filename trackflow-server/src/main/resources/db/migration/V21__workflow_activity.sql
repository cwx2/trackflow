-- ============================================================
-- 工作流变更审计日志表
-- 记录谁在什么时间修改了哪个工作流的转换规则
-- ============================================================

CREATE TABLE workflow_activity (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES project(id),
    issue_type VARCHAR(50),
    role_id BIGINT,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    action VARCHAR(50) NOT NULL,
    old_value VARCHAR(200),
    new_value VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引：按项目查询审计日志
CREATE INDEX idx_workflow_activity_project ON workflow_activity(project_id);
-- 索引：按时间倒序查询
CREATE INDEX idx_workflow_activity_created ON workflow_activity(created_at DESC);

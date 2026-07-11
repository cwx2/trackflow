-- ============================================================
-- 工作流转换规则表
-- ============================================================

CREATE TABLE workflow_transition (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    issue_type VARCHAR(50) NOT NULL DEFAULT '*',
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    old_status_id BIGINT NOT NULL REFERENCES issue_status(id),
    new_status_id BIGINT NOT NULL REFERENCES issue_status(id),
    conditions JSONB DEFAULT '{}',
    UNIQUE(project_id, issue_type, role_id, old_status_id, new_status_id)
);

CREATE INDEX idx_wf_lookup ON workflow_transition(project_id, issue_type, role_id, old_status_id);

-- 默认工作流（全局，所有类型，项目管理员角色可以做所有转换）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id) VALUES
-- project_admin (role_id=2) 全部状态互转
(NULL, '*', 2, 1, 2), (NULL, '*', 2, 1, 6),
(NULL, '*', 2, 2, 3), (NULL, '*', 2, 2, 4), (NULL, '*', 2, 2, 6),
(NULL, '*', 2, 3, 2), (NULL, '*', 2, 3, 4), (NULL, '*', 2, 3, 6),
(NULL, '*', 2, 4, 2), (NULL, '*', 2, 4, 5), (NULL, '*', 2, 4, 6),
(NULL, '*', 2, 5, 7), (NULL, '*', 2, 6, 7),
(NULL, '*', 2, 7, 2), (NULL, '*', 2, 7, 6),
-- developer (role_id=3)
(NULL, '*', 3, 1, 2), (NULL, '*', 3, 1, 6),
(NULL, '*', 3, 2, 3), (NULL, '*', 3, 2, 4), (NULL, '*', 3, 2, 6),
(NULL, '*', 3, 3, 2), (NULL, '*', 3, 3, 4),
(NULL, '*', 3, 7, 2),
-- tester (role_id=4)
(NULL, '*', 4, 4, 5), (NULL, '*', 4, 4, 2),
(NULL, '*', 4, 5, 7);

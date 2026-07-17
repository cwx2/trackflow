-- ============================================================================
-- V75: 工作流版本追踪表（乐观锁并发控制）
-- 用于检测并发编辑冲突，防止 lost update
-- ============================================================================

CREATE TABLE workflow_version (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT       REFERENCES project(id) ON DELETE CASCADE,
    issue_type  VARCHAR(50)  NOT NULL DEFAULT '*',
    role_id     BIGINT       NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    author      BOOLEAN      NOT NULL DEFAULT FALSE,
    assignee    BOOLEAN      NOT NULL DEFAULT FALSE,
    version     INT          NOT NULL DEFAULT 1,
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by  BIGINT       REFERENCES sys_user(id) ON DELETE SET NULL,

    -- 唯一约束：每个 (project, issueType, role, author, assignee) 组合只有一行版本记录
    CONSTRAINT uq_workflow_version_key UNIQUE (project_id, issue_type, role_id, author, assignee)
);

-- 为 null project_id（全局工作流）创建部分唯一索引
-- PostgreSQL 的 UNIQUE 约束不处理 NULL 等值比较，需要额外索引
CREATE UNIQUE INDEX uq_workflow_version_global
    ON workflow_version (issue_type, role_id, author, assignee)
    WHERE project_id IS NULL;

COMMENT ON TABLE workflow_version IS '工作流转换矩阵版本追踪，用于乐观锁并发控制';
COMMENT ON COLUMN workflow_version.version IS '版本号，每次更新递增，用于 CAS 校验';

-- 为已存在的工作流规则初始化版本记录（基于 workflow_transition 表中已有数据的唯一组合）
INSERT INTO workflow_version (project_id, issue_type, role_id, author, assignee, version, updated_at)
SELECT DISTINCT
    project_id,
    issue_type,
    role_id,
    COALESCE(author, FALSE),
    COALESCE(assignee, FALSE),
    1,
    NOW()
FROM workflow_transition
ON CONFLICT DO NOTHING;

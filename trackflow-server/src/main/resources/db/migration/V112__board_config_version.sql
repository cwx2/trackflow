-- ============================================================
-- V111: 看板配置乐观锁版本控制
-- 为看板配置的全量替换操作添加并发控制机制。
-- 每个项目维护一个 board 配置版本号，任何看板配置变更时
-- 通过 CAS 检查版本一致性，不一致则返回 409 Conflict。
-- ============================================================

-- 看板配置版本表：每个项目一行，用于乐观锁并发控制
CREATE TABLE IF NOT EXISTS board_config_version (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL,
    version     INTEGER NOT NULL DEFAULT 1,
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by  BIGINT,
    CONSTRAINT uk_board_config_version_project UNIQUE (project_id),
    CONSTRAINT fk_board_config_version_project FOREIGN KEY (project_id)
        REFERENCES project(id) ON DELETE CASCADE
);

-- 为已有看板配置的项目初始化版本记录
INSERT INTO board_config_version (project_id, version, updated_at)
SELECT DISTINCT project_id, 1, NOW()
FROM board_column_config
ON CONFLICT (project_id) DO NOTHING;

INSERT INTO board_config_version (project_id, version, updated_at)
SELECT DISTINCT project_id, 1, NOW()
FROM board_general_config
ON CONFLICT (project_id) DO NOTHING;

INSERT INTO board_config_version (project_id, version, updated_at)
SELECT DISTINCT project_id, 1, NOW()
FROM board_card_config
ON CONFLICT (project_id) DO NOTHING;

INSERT INTO board_config_version (project_id, version, updated_at)
SELECT DISTINCT project_id, 1, NOW()
FROM board_swimlane_config
ON CONFLICT (project_id) DO NOTHING;

INSERT INTO board_config_version (project_id, version, updated_at)
SELECT DISTINCT project_id, 1, NOW()
FROM board_column_merge
ON CONFLICT (project_id) DO NOTHING;

COMMENT ON TABLE board_config_version IS '看板配置版本表（乐观锁并发控制）';
COMMENT ON COLUMN board_config_version.project_id IS '项目ID，每项目一行';
COMMENT ON COLUMN board_config_version.version IS '版本号，每次保存递增';
COMMENT ON COLUMN board_config_version.updated_at IS '最后更新时间';
COMMENT ON COLUMN board_config_version.updated_by IS '最后更新人 ID';

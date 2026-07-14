-- V30: 创建 transition_action 表（转换动作引擎）
-- 功能：存储状态转换后自动执行的动作配置（首期：Auto-Assign）
-- 设计：与 workflow_transition（权限规则）分离，独立管理转换动作

CREATE TABLE transition_action (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT REFERENCES project(id),          -- NULL = 全局默认
    issue_type      VARCHAR(50) NOT NULL DEFAULT '*',       -- Bug/Task/Feature/* (通配符)
    old_status_id   BIGINT NOT NULL REFERENCES issue_status(id),
    new_status_id   BIGINT NOT NULL REFERENCES issue_status(id),
    action_type     VARCHAR(50) NOT NULL,                   -- 'auto_assign' (可扩展)
    action_config   JSONB NOT NULL DEFAULT '{}',            -- 策略配置 JSON
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      BIGINT REFERENCES sys_user(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引：按项目+类型+转换路径查询（最常用的查询模式）
CREATE INDEX idx_ta_transition_path
    ON transition_action(project_id, issue_type, old_status_id, new_status_id);

-- 索引：全局默认动作查询（project_id IS NULL 的部分索引）
CREATE INDEX idx_ta_global_path
    ON transition_action(old_status_id, new_status_id)
    WHERE project_id IS NULL;

-- 索引：按启用状态过滤
CREATE INDEX idx_ta_enabled
    ON transition_action(enabled);

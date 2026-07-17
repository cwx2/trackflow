-- notification_preference 表新增 project_id 列，支持项目级偏好覆盖全局
-- 参考 OpenProject notification_settings 表设计

-- 1. 新增 project_id 列（NULL 表示全局偏好）
ALTER TABLE notification_preference ADD COLUMN project_id BIGINT NULL;

-- 2. 删除旧的唯一索引（原来是 user_id 全表唯一）
DROP INDEX IF EXISTS uk_notification_pref_user;

-- 3. 创建条件唯一索引：全局偏好（每用户仅一条 project_id IS NULL 记录）
CREATE UNIQUE INDEX uk_notification_pref_user_global
    ON notification_preference (user_id)
    WHERE project_id IS NULL;

-- 4. 创建条件唯一索引：项目级偏好（每用户每项目仅一条记录）
CREATE UNIQUE INDEX uk_notification_pref_user_project
    ON notification_preference (user_id, project_id)
    WHERE project_id IS NOT NULL;

-- 5. 外键约束
ALTER TABLE notification_preference
    ADD CONSTRAINT fk_notification_pref_project
    FOREIGN KEY (project_id) REFERENCES project(id)
    ON DELETE CASCADE;

-- 6. 复合索引用于快速查询指定用户+项目的偏好
CREATE INDEX idx_notification_pref_user_project
    ON notification_preference (user_id, project_id);

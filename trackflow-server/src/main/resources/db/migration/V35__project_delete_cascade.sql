-- V35: 将 issue、issue_tag、transition_action、workflow_activity 的 project_id FK 改为 ON DELETE CASCADE
-- 用途：支持项目删除时级联清理所有关联数据

-- 1. issue 表：project_id FK → CASCADE
ALTER TABLE issue DROP CONSTRAINT issue_project_id_fkey;
ALTER TABLE issue ADD CONSTRAINT issue_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

-- 2. issue_tag 表：project_id FK → CASCADE
ALTER TABLE issue_tag DROP CONSTRAINT issue_tag_project_id_fkey;
ALTER TABLE issue_tag ADD CONSTRAINT issue_tag_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

-- 3. transition_action 表：project_id FK → CASCADE
ALTER TABLE transition_action DROP CONSTRAINT transition_action_project_id_fkey;
ALTER TABLE transition_action ADD CONSTRAINT transition_action_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

-- 4. workflow_activity 表：project_id FK → CASCADE
ALTER TABLE workflow_activity DROP CONSTRAINT workflow_activity_project_id_fkey;
ALTER TABLE workflow_activity ADD CONSTRAINT workflow_activity_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

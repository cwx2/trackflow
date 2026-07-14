-- V33: 清理全局工作流中的重复记录
-- 问题：全局工作流（project_id IS NULL）存在重复的 (issue_type, role_id, old_status_id, new_status_id) 组合
-- 影响：ProjectInitializationService 复制工作流到新项目时因唯一约束冲突导致 500 错误

-- 删除重复记录，保留 id 最小的那条
DELETE FROM workflow_transition
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY issue_type, role_id, old_status_id, new_status_id
                   ORDER BY id
               ) as rn
        FROM workflow_transition
        WHERE project_id IS NULL
    ) sub
    WHERE rn > 1
);

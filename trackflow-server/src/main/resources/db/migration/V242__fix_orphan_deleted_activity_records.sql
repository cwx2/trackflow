-- V242__fix_orphan_deleted_activity_records.sql
-- 修复孤立的 deleted 活动记录：为缺少对应 restored 记录的工单补充恢复活动
--
-- 问题背景：
-- 某些工单被软删除后，通过直接 SQL 恢复（UPDATE issue SET deleted_at = NULL），
-- 绕过了系统的 restore() 方法，导致 issue_activity 中有 deleted 记录但没有 restored 记录。
-- 这使得活动历史显示"删除了此工单"但工单仍然存在，造成审计日志不一致。
--
-- 受影响工单（2026-08-02 查询）：
-- - DE4-1473: 1 次 deleted, 0 次 restored
-- - FE1-53: 1 次 deleted, 0 次 restored
-- - DE4-1434: 3 次 deleted, 2 次 restored (差 1 次)
--
-- 幂等性：如果数据已手动修复，此脚本不会重复插入任何记录。

-- 为每个缺少 restored 记录的 deleted 活动补充对应的 restored 记录
-- 使用原 deleted 活动的 created_at + 1 秒作为恢复时间
INSERT INTO issue_activity (issue_id, user_id, action, created_at)
SELECT 
    lud.issue_id,
    lud.user_id,
    'restored',
    lud.deleted_at + interval '1 second'
FROM (
    -- 对于每个需要修复的工单，取最后一条 deleted 活动作为恢复的基准
    SELECT DISTINCT ON (inf.issue_id)
        inf.issue_id,
        ia.user_id,
        ia.created_at as deleted_at
    FROM (
        -- 筛选出 deleted 次数 > restored 次数的工单
        SELECT 
            i.id as issue_id,
            (SELECT COUNT(*) FROM issue_activity ia WHERE ia.issue_id = i.id AND ia.action = 'deleted') as delete_count,
            (SELECT COUNT(*) FROM issue_activity ia WHERE ia.issue_id = i.id AND ia.action = 'restored') as restore_count
        FROM issue i
        WHERE i.deleted_at IS NULL
        AND EXISTS (
            SELECT 1 FROM issue_activity ia 
            WHERE ia.issue_id = i.id AND ia.action = 'deleted'
        )
    ) inf
    JOIN issue_activity ia ON ia.issue_id = inf.issue_id AND ia.action = 'deleted'
    WHERE inf.delete_count > inf.restore_count
    ORDER BY inf.issue_id, ia.created_at DESC
) lud;

-- 如果有记录被插入，打印通知（便于排查）
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM (
        SELECT 
            i.id,
            (SELECT COUNT(*) FROM issue_activity ia WHERE ia.issue_id = i.id AND ia.action = 'deleted') as d,
            (SELECT COUNT(*) FROM issue_activity ia WHERE ia.issue_id = i.id AND ia.action = 'restored') as r
        FROM issue i
        WHERE i.deleted_at IS NULL
        AND EXISTS (SELECT 1 FROM issue_activity ia WHERE ia.issue_id = i.id AND ia.action = 'deleted')
    ) t
    WHERE d > r;
    
    IF orphan_count = 0 THEN
        RAISE NOTICE 'V242: No orphan deleted activity records found (data may have been fixed manually)';
    ELSE
        RAISE NOTICE 'V242: Fixed % orphan deleted activity records', orphan_count;
    END IF;
END $$;

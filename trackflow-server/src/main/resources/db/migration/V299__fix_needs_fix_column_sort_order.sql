-- V299__fix_needs_fix_column_sort_order.sql
-- 修复 V298 后的 sort_order 冲突：Needs Fix 和 Done 都是 sort_order=4。
-- 将 Needs Fix (status_id=19) 之后的所有列 sort_order +1，确保唯一递增。

UPDATE board_column_config
SET sort_order = sort_order + 1, updated_at = NOW()
WHERE status_id != 19
  AND sort_order >= (
      SELECT bcc19.sort_order
      FROM board_column_config bcc19
      WHERE bcc19.project_id = board_column_config.project_id
        AND bcc19.status_id = 19
  )
  AND project_id IN (
      SELECT project_id FROM board_column_config WHERE status_id = 19
  );

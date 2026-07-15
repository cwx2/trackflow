-- V39: Fix corrupted title for issue DE4-1393
-- The title was stored as '??????????' (literal question marks) due to encoding issue during data insertion.
-- Restore a meaningful title based on the issue's description content.

UPDATE issue
SET title = '设置页面暗色模式样式问题',
    updated_at = NOW()
WHERE issue_key = 'DE4-1393'
  AND title = '??????????';

-- V243: 清理撤销状态变更时错误生成的普通评论
-- 之前 undoTransitStatus 会以"撤销状态变更"为内容创建普通评论，
-- 这些评论没有实际信息价值，应该删除。
-- 撤销操作现在改为记录 status_reverted 类型的活动记录。

DELETE FROM issue_comment
WHERE content = '撤销状态变更'
  AND deleted_at IS NULL

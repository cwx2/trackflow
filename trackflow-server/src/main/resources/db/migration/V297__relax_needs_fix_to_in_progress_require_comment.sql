-- 将 "Needs Fix → In Progress" 转换的 require_comment 改为 false
-- 开发人员从"待修复"恢复工作是日常高频操作，不应强制填写理由
-- 参考 YouTrack：普通状态前进/回退操作不要求填写理由

UPDATE workflow_transition
SET require_comment = false
WHERE old_status_id = 19
  AND new_status_id = 2
  AND require_comment = true;

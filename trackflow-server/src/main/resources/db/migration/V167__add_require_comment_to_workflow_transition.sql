-- V167__add_require_comment_to_workflow_transition.sql
-- 为工作流转换规则增加"强制评论"配置，支持管理员要求某些状态转换必须附带理由
-- 典型场景：测试退回（Testing→In Progress）、重新打开（Done→Reopened）

ALTER TABLE workflow_transition ADD COLUMN require_comment BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN workflow_transition.require_comment IS '是否要求此转换必须附带评论/理由（true=强制填写，false=可选）';

-- 为常见的"退回"和"重新打开"转换设置默认强制评论
-- Testing(4) → In Progress(2): 测试退回
UPDATE workflow_transition SET require_comment = TRUE
WHERE old_status_id = 4 AND new_status_id = 2;

-- Testing(4) → Open(1): 测试退回到 Open
UPDATE workflow_transition SET require_comment = TRUE
WHERE old_status_id = 4 AND new_status_id = 1;

-- Done(5) → Reopened(7): 重新打开已完成工单
UPDATE workflow_transition SET require_comment = TRUE
WHERE old_status_id = 5 AND new_status_id = 7;

-- Done(5) → In Progress(2): 重新打开回到进行中
UPDATE workflow_transition SET require_comment = TRUE
WHERE old_status_id = 5 AND new_status_id = 2;

-- Cancelled(6) → Reopened(7): 重新打开已取消工单
UPDATE workflow_transition SET require_comment = TRUE
WHERE old_status_id = 6 AND new_status_id = 7;

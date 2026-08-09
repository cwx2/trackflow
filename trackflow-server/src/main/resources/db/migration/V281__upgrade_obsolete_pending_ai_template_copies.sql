-- 仅修复 V275 第一版“定时巡检并 AI 处理待办工单”的克隆副本。
-- statusName + loop-1 是该废弃模板独有的端口指纹；它引用的节点和连线已不存在，
-- 因而不能安全地保留为 AI 自动写入流程。升级为当前已验证的只读待办检测模板，
-- 同时清除过期发布快照并停止运行，防止旧定义继续执行。

WITH repaired_template AS (
    SELECT definition
    FROM automation_workflow_template
    WHERE is_builtin = TRUE AND name = '待办工单检测'
    LIMIT 1
)
UPDATE automation_workflow workflow
SET definition = repaired_template.definition,
    published_definition = NULL,
    status = 'draft',
    runtime_enabled = FALSE,
    name = '待办工单检测（已升级）',
    version = COALESCE(version, 0) + 1,
    updated_at = NOW()
FROM repaired_template
WHERE workflow.definition LIKE '%"statusName"%'
  AND workflow.definition LIKE '%"loop-1"%'
  AND workflow.name LIKE '定时巡检并 AI 处理待办工单%';

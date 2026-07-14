-- V29: 修复"待我测试"查询 - 移除 assignee 条件
-- 问题：REQ-028 测试人员看到"待我测试"显示0个工单
-- 原因：查询条件包含 assignee=${currentUser}，但 Testing 工单并非分配给测试人员
-- 修复：移除 assignee 条件，测试人员应看到所在项目中所有 Testing 状态的工单
--       项目范围过滤由 QueryExecutor.executeWithProjectFilter() 自动处理

UPDATE saved_query
SET filters = '[{"field":"status","operator":"in","value":["testing"]}]'::jsonb,
    updated_at = NOW()
WHERE name = '待我测试' AND shared = true;

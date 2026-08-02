-- REQ-85: 修复"待我测试"保存搜索，添加测试人员字段筛选
-- 问题：测试人员需要能方便地找到分配给自己的待测试工单
-- 方案：更新"待我测试"的筛选条件，添加 customField.测试人员 = ${currentUser}
--       测试人员字段 ID = 2079182531237601281

-- 更新"待我测试"保存搜索，添加测试人员筛选
UPDATE saved_query
SET filters = '[
  {"field": "status", "value": ["testing"], "operator": "in"},
  {"field": "cf.2079182531237601281", "value": ["${currentUser}"], "operator": "eq"}
]'::jsonb,
    updated_at = NOW()
WHERE id = 13
  AND name = '待我测试';

-- 确保更新成功
DO $$
BEGIN
  IF NOT FOUND THEN
    RAISE NOTICE '警告: 未找到 id=13 且 name=待我测试 的保存搜索';
  END IF;
END $$;

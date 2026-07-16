-- ============================================================
-- 工作流审计日志增强：增加详情列
-- 存储 JSON 格式的变更明细（新增/删除了哪些转换），便于人类可读展示
-- ============================================================

-- 添加 details 列存储变更明细 JSON
ALTER TABLE workflow_activity ADD COLUMN details TEXT;

-- 添加 summary 列存储人类可读的变更摘要
ALTER TABLE workflow_activity ADD COLUMN summary VARCHAR(500);

-- 扩大 old_value / new_value 列长度（原 200 不够）
ALTER TABLE workflow_activity ALTER COLUMN old_value TYPE VARCHAR(500);
ALTER TABLE workflow_activity ALTER COLUMN new_value TYPE VARCHAR(500);

-- 添加用户ID索引（按操作人筛选）
CREATE INDEX idx_workflow_activity_user ON workflow_activity(user_id);

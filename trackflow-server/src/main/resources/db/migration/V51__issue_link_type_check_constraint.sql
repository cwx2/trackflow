-- V51: 添加 issue_link.link_type CHECK 约束，确保只允许合法的关联类型

-- 1. 清洗现有数据：将 depends_on 迁移为 blocked_by（语义等价：A depends_on B ≈ A blocked_by B）
UPDATE issue_link SET link_type = 'blocked_by' WHERE link_type = 'depends_on';

-- 2. 清洗其他可能的非法值（安全兜底）
DELETE FROM issue_link
WHERE link_type NOT IN ('blocks', 'blocked_by', 'duplicates', 'duplicated_by', 'parent_of', 'child_of', 'relates_to');

-- 3. 添加 CHECK 约束
ALTER TABLE issue_link ADD CONSTRAINT chk_issue_link_type
    CHECK (link_type IN ('blocks', 'blocked_by', 'duplicates', 'duplicated_by', 'parent_of', 'child_of', 'relates_to'));

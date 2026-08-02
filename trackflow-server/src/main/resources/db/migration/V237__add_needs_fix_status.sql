-- V237__add_needs_fix_status.sql
-- 添加"待修复"状态，用于区分被测试打回的工单与正常进行中的工单
-- 关联需求：REQ-47
--
-- 角色 ID 参考：
-- 2 = 项目管理员, 3 = 开发人员, 4 = 测试人员, 5 = 观察者, 6 = 产品经理, 7 = 技术负责人
-- 状态 ID 参考：
-- 2 = 进行中, 4 = 测试中, 6 = 已取消, 19 = 待修复(本脚本新增)

-- 1. 添加新状态 "Needs Fix"（待修复）
INSERT INTO issue_status (id, name, display_name, code, color, category, is_default, is_closed, sort_order, created_at)
VALUES (19, 'Needs Fix', '待修复', 'needs_fix', '#FF5722', 'in_progress', false, false, 19, NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. 更新工作流转换：将"测试中"→"进行中"改为"测试中"→"待修复"
-- 这样测试人员打回工单时，状态变为"待修复"而非"进行中"，从而在视觉上区分开

-- 2.1 更新全局规则（project_id IS NULL）
UPDATE workflow_transition 
SET new_status_id = 19
WHERE project_id IS NULL 
  AND old_status_id = 4 
  AND new_status_id = 2;

-- 2.2 更新所有项目级规则
UPDATE workflow_transition 
SET new_status_id = 19
WHERE project_id IS NOT NULL 
  AND old_status_id = 4 
  AND new_status_id = 2;

-- 3. 添加"待修复"的出口转换（全局规则）
-- 开发人员(3)：待修复 → 测试中（修复后提测）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 3, 19, 4, false
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 3 AND old_status_id = 19 AND new_status_id = 4
);

-- 开发人员(3)：待修复 → 进行中（恢复正常状态，需说明原因）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 3, 19, 2, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 3 AND old_status_id = 19 AND new_status_id = 2
);

-- 开发人员(3)：待修复 → 已取消
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 3, 19, 6, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 3 AND old_status_id = 19 AND new_status_id = 6
);

-- 技术负责人(7)：待修复 → 测试中
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 7, 19, 4, false
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 7 AND old_status_id = 19 AND new_status_id = 4
);

-- 技术负责人(7)：待修复 → 进行中
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 7, 19, 2, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 7 AND old_status_id = 19 AND new_status_id = 2
);

-- 技术负责人(7)：待修复 → 已取消
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 7, 19, 6, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 7 AND old_status_id = 19 AND new_status_id = 6
);

-- 项目管理员(2)：待修复 → 测试中
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 2, 19, 4, false
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 2 AND old_status_id = 19 AND new_status_id = 4
);

-- 项目管理员(2)：待修复 → 进行中
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 2, 19, 2, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 2 AND old_status_id = 19 AND new_status_id = 2
);

-- 项目管理员(2)：待修复 → 已取消
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 2, 19, 6, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 2 AND old_status_id = 19 AND new_status_id = 6
);

-- 产品经理(6)：待修复 → 测试中
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 6, 19, 4, false
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 6 AND old_status_id = 19 AND new_status_id = 4
);

-- 产品经理(6)：待修复 → 进行中
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 6, 19, 2, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 6 AND old_status_id = 19 AND new_status_id = 2
);

-- 产品经理(6)：待修复 → 已取消
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 6, 19, 6, true
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 6 AND old_status_id = 19 AND new_status_id = 6
);

-- 测试人员(4)：待修复 → 测试中（复测）
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT NULL, '*', 4, 19, 4, false
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_transition 
    WHERE project_id IS NULL AND role_id = 4 AND old_status_id = 19 AND new_status_id = 4
);

-- 4. 为现有项目复制新的转换规则（ON CONFLICT DO NOTHING 处理已存在的规则）
-- 开发人员(3)
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 3, 19, 4, false
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 3, 19, 2, true
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- 技术负责人(7)
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 7, 19, 4, false
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 7, 19, 2, true
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- 项目管理员(2)
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 2, 19, 4, false
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 2, 19, 2, true
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- 测试人员(4)
INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, require_comment)
SELECT DISTINCT wt.project_id, '*', 4, 19, 4, false
FROM workflow_transition wt
WHERE wt.project_id IS NOT NULL
ON CONFLICT DO NOTHING;

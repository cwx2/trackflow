-- REQ-363: 为 transition_action.action_type 添加 CHECK 约束，限制为有效枚举值
-- 未来新增动作类型时需通过新的迁移脚本扩展此约束

-- 先确认现有数据合法（若有非法数据则此语句会失败，需先清理）
ALTER TABLE transition_action
    ADD CONSTRAINT chk_action_type
    CHECK (action_type IN ('auto_assign'));

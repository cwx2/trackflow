-- 预置 Scrum 字段: Ideal Days (period) 和 Story Points (integer)
-- 对标 YouTrack Scrum 项目模板默认字段

-- Ideal Days: 理想工时，period 类型，用于 Task 类型工单
INSERT INTO custom_field_definition (id, name, field_format, is_required, is_for_all, is_multi, is_hidden_in_list, is_auto_attach, position, created_at, updated_at)
VALUES (1920000000000001, 'Ideal Days', 'period', FALSE, TRUE, FALSE, FALSE, FALSE, 900, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Story Points: 故事点，integer 类型，用于 User Story 类型工单
INSERT INTO custom_field_definition (id, name, field_format, is_required, is_for_all, is_multi, is_hidden_in_list, is_auto_attach, position, created_at, updated_at)
VALUES (1920000000000002, 'Story Points', 'int', FALSE, TRUE, FALSE, FALSE, FALSE, 901, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

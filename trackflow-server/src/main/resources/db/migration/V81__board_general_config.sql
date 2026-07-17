-- 看板基本设置表（General 标签页配置）
-- 存储看板名称和访问权限配置
CREATE TABLE board_general_config (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL DEFAULT '',
    can_view_roles JSONB NOT NULL DEFAULT '["project_admin","tech_lead","developer","product_manager","tester","observer"]'::jsonb,
    can_edit_roles JSONB NOT NULL DEFAULT '["project_admin","tech_lead"]'::jsonb,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_general_config_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

COMMENT ON TABLE board_general_config IS '看板基本设置（名称、访问权限）';
COMMENT ON COLUMN board_general_config.name IS '看板显示名称，为空时前端使用"项目名 + 看板"';
COMMENT ON COLUMN board_general_config.can_view_roles IS '可查看看板的角色代码列表（JSONB 数组）';
COMMENT ON COLUMN board_general_config.can_edit_roles IS '可编辑看板设置的角色代码列表（JSONB 数组）';

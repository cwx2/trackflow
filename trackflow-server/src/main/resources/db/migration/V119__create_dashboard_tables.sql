-- ============================================================
-- V119: Create Dashboard + Widget tables for customizable dashboards
-- REQ-467-2: Dashboard + Widget data model + CRUD API
-- ============================================================

-- Dashboard 仪表盘主表
CREATE TABLE dashboard (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id),
    shared BOOLEAN NOT NULL DEFAULT false,
    layout JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE dashboard IS '自定义仪表盘';
COMMENT ON COLUMN dashboard.name IS '仪表盘名称';
COMMENT ON COLUMN dashboard.description IS '描述';
COMMENT ON COLUMN dashboard.owner_id IS '创建人ID';
COMMENT ON COLUMN dashboard.shared IS '是否共享（全局可见）';
COMMENT ON COLUMN dashboard.layout IS '布局配置（预留，暂由 widget 自身 position 控制）';

CREATE INDEX idx_dashboard_owner ON dashboard(owner_id);

-- Dashboard Widget 微件表
CREATE TABLE dashboard_widget (
    id BIGSERIAL PRIMARY KEY,
    dashboard_id BIGINT NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    widget_type VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    config JSONB NOT NULL DEFAULT '{}',
    report_id BIGINT REFERENCES report_definition(id) ON DELETE SET NULL,
    position_x INT NOT NULL DEFAULT 0,
    position_y INT NOT NULL DEFAULT 0,
    width INT NOT NULL DEFAULT 4,
    height INT NOT NULL DEFAULT 3,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE dashboard_widget IS '仪表盘微件';
COMMENT ON COLUMN dashboard_widget.dashboard_id IS '所属仪表盘';
COMMENT ON COLUMN dashboard_widget.widget_type IS '微件类型（note/report/issue_list/number_card等）';
COMMENT ON COLUMN dashboard_widget.title IS '微件标题（可自定义）';
COMMENT ON COLUMN dashboard_widget.config IS '微件配置（每种类型不同）';
COMMENT ON COLUMN dashboard_widget.report_id IS '关联报表定义（报表类微件）';
COMMENT ON COLUMN dashboard_widget.position_x IS '网格列位置（0-based）';
COMMENT ON COLUMN dashboard_widget.position_y IS '网格行位置（0-based）';
COMMENT ON COLUMN dashboard_widget.width IS '占几列（1-12，默认4）';
COMMENT ON COLUMN dashboard_widget.height IS '占几行（默认3）';
COMMENT ON COLUMN dashboard_widget.sort_order IS '排序序号';

CREATE INDEX idx_dashboard_widget_dashboard ON dashboard_widget(dashboard_id);
CREATE INDEX idx_dashboard_widget_report ON dashboard_widget(report_id) WHERE report_id IS NOT NULL;

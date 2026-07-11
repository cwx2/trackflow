-- ============================================================
-- 自定义字段模块（Custom Fields）
-- ============================================================

-- 自定义字段定义表
CREATE TABLE custom_field_definition (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    field_format    VARCHAR(30) NOT NULL,
    is_required     BOOLEAN NOT NULL DEFAULT false,
    is_for_all      BOOLEAN NOT NULL DEFAULT false,
    default_value   TEXT,
    min_length      INTEGER NOT NULL DEFAULT 0,
    max_length      INTEGER NOT NULL DEFAULT 0,
    regexp          VARCHAR(255),
    position        INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT uk_custom_field_name UNIQUE (name)
);

COMMENT ON TABLE custom_field_definition IS '自定义字段定义（管理员配置）';
COMMENT ON COLUMN custom_field_definition.field_format IS '字段类型: string, int, float, date, bool, list, user';
COMMENT ON COLUMN custom_field_definition.is_for_all IS 'true=全局字段(所有项目可用), false=需关联到具体项目';

-- 列表类型选项表
CREATE TABLE custom_field_option (
    id              BIGSERIAL PRIMARY KEY,
    custom_field_id BIGINT NOT NULL REFERENCES custom_field_definition(id) ON DELETE CASCADE,
    value           VARCHAR(256) NOT NULL,
    position        INTEGER NOT NULL DEFAULT 0,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cf_option_field ON custom_field_option(custom_field_id);
COMMENT ON TABLE custom_field_option IS '列表类型自定义字段的可选值';

-- EAV 值表
CREATE TABLE custom_field_value (
    id              BIGSERIAL PRIMARY KEY,
    issue_id        BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    custom_field_id BIGINT NOT NULL REFERENCES custom_field_definition(id) ON DELETE CASCADE,
    value           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cf_value_issue_field UNIQUE (issue_id, custom_field_id)
);

CREATE INDEX idx_cf_value_issue ON custom_field_value(issue_id);
CREATE INDEX idx_cf_value_field ON custom_field_value(custom_field_id);
COMMENT ON TABLE custom_field_value IS 'Issue 自定义字段值（EAV 模式）';
COMMENT ON COLUMN custom_field_value.value IS '所有类型统一存为 TEXT，应用层负责类型转换和验证';

-- 字段-项目关联表（多对多）
CREATE TABLE custom_field_project (
    id              BIGSERIAL PRIMARY KEY,
    custom_field_id BIGINT NOT NULL REFERENCES custom_field_definition(id) ON DELETE CASCADE,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT uk_cf_project UNIQUE (custom_field_id, project_id)
);

COMMENT ON TABLE custom_field_project IS '自定义字段与项目的关联（is_for_all=false 时生效）';

-- 字段-Issue类型关联表（多对多）
CREATE TABLE custom_field_issue_type (
    id              BIGSERIAL PRIMARY KEY,
    custom_field_id BIGINT NOT NULL REFERENCES custom_field_definition(id) ON DELETE CASCADE,
    issue_type      VARCHAR(50) NOT NULL,
    CONSTRAINT uk_cf_issue_type UNIQUE (custom_field_id, issue_type)
);

COMMENT ON TABLE custom_field_issue_type IS '自定义字段与 Issue 类型的关联（空=适用所有类型）';

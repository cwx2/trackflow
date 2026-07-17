-- V67: 工作项属性（Work Item Attributes）
-- 参考 YouTrack 的 Work Item Attributes 功能
-- 属性系统级定义，可分配到项目使用

-- 1. 工作项属性定义表
CREATE TABLE work_item_attribute (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    is_builtin BOOLEAN NOT NULL DEFAULT FALSE,  -- 内置属性不可删除（如 Work type）
    position INT NOT NULL DEFAULT 0,            -- 排序位置
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES sys_user(id),
    updated_by BIGINT REFERENCES sys_user(id)
);

-- 属性名唯一
CREATE UNIQUE INDEX uk_work_item_attribute_name ON work_item_attribute(LOWER(name));

COMMENT ON TABLE work_item_attribute IS '工作项属性定义（系统级）';
COMMENT ON COLUMN work_item_attribute.is_builtin IS '内置属性（如 Work type），不可删除';

-- 2. 工作项属性值表（每个属性有多个可选值）
CREATE TABLE work_item_attribute_value (
    id BIGSERIAL PRIMARY KEY,
    attribute_id BIGINT NOT NULL REFERENCES work_item_attribute(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),                          -- 颜色（如 #58a6ff）
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wia_value_attribute ON work_item_attribute_value(attribute_id);
CREATE UNIQUE INDEX uk_wia_value_name ON work_item_attribute_value(attribute_id, LOWER(name));

COMMENT ON TABLE work_item_attribute_value IS '工作项属性的可选值';
COMMENT ON COLUMN work_item_attribute_value.color IS '值的颜色标识';

-- 3. 工作项属性-项目关联表
CREATE TABLE work_item_attribute_project (
    id BIGSERIAL PRIMARY KEY,
    attribute_id BIGINT NOT NULL REFERENCES work_item_attribute(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_wia_project ON work_item_attribute_project(attribute_id, project_id);
CREATE INDEX idx_wia_project_project ON work_item_attribute_project(project_id);

COMMENT ON TABLE work_item_attribute_project IS '工作项属性与项目的关联（属性在哪些项目中启用）';

-- 4. 工时记录-属性值关联表（多对多，一条工时可有多个属性值）
CREATE TABLE time_entry_attribute_value (
    id BIGSERIAL PRIMARY KEY,
    time_entry_id BIGINT NOT NULL REFERENCES time_entry(id) ON DELETE CASCADE,
    attribute_id BIGINT NOT NULL REFERENCES work_item_attribute(id) ON DELETE CASCADE,
    value_id BIGINT NOT NULL REFERENCES work_item_attribute_value(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_tea_value ON time_entry_attribute_value(time_entry_id, attribute_id);
CREATE INDEX idx_tea_time_entry ON time_entry_attribute_value(time_entry_id);
CREATE INDEX idx_tea_value_id ON time_entry_attribute_value(value_id);

COMMENT ON TABLE time_entry_attribute_value IS '工时记录与工作项属性值的关联';

-- 5. 种子数据：创建内置的"Work type"属性并迁移现有数据
INSERT INTO work_item_attribute (name, is_builtin, position) VALUES ('Work type', TRUE, 0);

-- 插入默认的工作类型值
INSERT INTO work_item_attribute_value (attribute_id, name, color, position)
SELECT wa.id, v.name, v.color, v.pos
FROM work_item_attribute wa,
     (VALUES
         ('Development', '#58a6ff', 0),
         ('Testing',     '#3fb950', 1),
         ('Documentation', '#d29922', 2),
         ('Design',      '#a371f7', 3),
         ('Review',      '#f0883e', 4),
         ('Meeting',     '#8b949e', 5),
         ('Other',       '#6e7681', 6)
     ) AS v(name, color, pos)
WHERE wa.name = 'Work type';

-- 6. 将内置"Work type"属性分配到所有现有项目（已有时间追踪数据的项目）
INSERT INTO work_item_attribute_project (attribute_id, project_id)
SELECT wa.id, p.id
FROM work_item_attribute wa, project p
WHERE wa.name = 'Work type'
  AND p.status != 'deleted'
ON CONFLICT DO NOTHING;

-- 7. 迁移现有 time_entry.work_type 数据到新关联表
INSERT INTO time_entry_attribute_value (time_entry_id, attribute_id, value_id)
SELECT te.id, wa.id, wav.id
FROM time_entry te
JOIN work_item_attribute wa ON wa.name = 'Work type'
JOIN work_item_attribute_value wav ON wav.attribute_id = wa.id AND wav.name = te.work_type
WHERE te.work_type IS NOT NULL AND te.work_type != '';

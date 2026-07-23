-- V179__work_item_attribute_value_project.sql
-- 新增属性值-项目关联表，支持项目级独立管理属性值的可见性
-- 参考 YouTrack: 每个项目可以独立管理其属性值子集

-- 1. 创建属性值-项目关联表
CREATE TABLE work_item_attribute_value_project (
    id          BIGSERIAL PRIMARY KEY,
    value_id    BIGINT NOT NULL REFERENCES work_item_attribute_value(id) ON DELETE CASCADE,
    project_id  BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    position    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(value_id, project_id)
);

-- 2. 添加索引
CREATE INDEX idx_wavp_value_id ON work_item_attribute_value_project(value_id);
CREATE INDEX idx_wavp_project_id ON work_item_attribute_value_project(project_id);

-- 3. 数据迁移：为所有现有的属性-项目关联，自动生成对应的值-项目关联
-- 逻辑：如果属性A关联了项目P，则属性A的所有值也自动关联到项目P
INSERT INTO work_item_attribute_value_project (value_id, project_id, position, created_at)
SELECT v.id, ap.project_id, v.position, now()
FROM work_item_attribute_value v
JOIN work_item_attribute_project ap ON ap.attribute_id = v.attribute_id
ON CONFLICT (value_id, project_id) DO NOTHING;

COMMENT ON TABLE work_item_attribute_value_project IS '属性值-项目关联表，控制每个项目可见的属性值子集';
COMMENT ON COLUMN work_item_attribute_value_project.value_id IS '属性值ID';
COMMENT ON COLUMN work_item_attribute_value_project.project_id IS '项目ID';
COMMENT ON COLUMN work_item_attribute_value_project.position IS '值在项目中的排序位置';

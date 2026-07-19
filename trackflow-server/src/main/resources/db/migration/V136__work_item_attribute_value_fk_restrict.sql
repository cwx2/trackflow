-- V136: 修改 time_entry_attribute_value 的 FK 策略从 CASCADE 改为 RESTRICT
-- 防止删除属性值时静默级联删除工时记录的分类数据
-- REQ-622: work_item_attribute_value ON DELETE CASCADE 静默销毁工时分类历史

-- 1. 修改 value_id 的 FK 策略：CASCADE → RESTRICT
ALTER TABLE time_entry_attribute_value
    DROP CONSTRAINT time_entry_attribute_value_value_id_fkey;

ALTER TABLE time_entry_attribute_value
    ADD CONSTRAINT time_entry_attribute_value_value_id_fkey
    FOREIGN KEY (value_id) REFERENCES work_item_attribute_value(id) ON DELETE RESTRICT;

-- 2. 修改 attribute_id 的 FK 策略：CASCADE → RESTRICT
-- 属性本体删除时同样不应级联清除工时分类数据
ALTER TABLE time_entry_attribute_value
    DROP CONSTRAINT time_entry_attribute_value_attribute_id_fkey;

ALTER TABLE time_entry_attribute_value
    ADD CONSTRAINT time_entry_attribute_value_attribute_id_fkey
    FOREIGN KEY (attribute_id) REFERENCES work_item_attribute(id) ON DELETE RESTRICT;

-- 注意：time_entry_attribute_value.time_entry_id 保持 ON DELETE CASCADE
-- 因为工时记录本身被删除时，其分类关联也应该随之删除（这是正确的行为）

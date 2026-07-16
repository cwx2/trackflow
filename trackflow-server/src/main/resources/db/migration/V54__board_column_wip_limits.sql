-- 为看板列配置添加 WIP 限制字段
ALTER TABLE board_column_config
    ADD COLUMN wip_min INTEGER DEFAULT NULL,
    ADD COLUMN wip_max INTEGER DEFAULT NULL;

COMMENT ON COLUMN board_column_config.wip_min IS '最小在制品数量（低于此数时列标题显示黄色警告）';
COMMENT ON COLUMN board_column_config.wip_max IS '最大在制品数量（超过此数时列标题显示红色警告）';

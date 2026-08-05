-- V263__add_canvas_position_to_issue_status.sql
-- 为 issue_status 表新增画布坐标字段，支持工作流状态机可视化画布中节点的位置持久化

ALTER TABLE issue_status ADD COLUMN canvas_x DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE issue_status ADD COLUMN canvas_y DOUBLE PRECISION DEFAULT NULL;

COMMENT ON COLUMN issue_status.canvas_x IS '状态节点在工作流画布中的 X 坐标';
COMMENT ON COLUMN issue_status.canvas_y IS '状态节点在工作流画布中的 Y 坐标';

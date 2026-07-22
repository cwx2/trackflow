-- V171__report_result_cache.sql
-- 为 report_definition 表增加结果缓存字段，实现报表结果持久化机制
-- 对标 YouTrack："Once calculated, the report is saved in the database and is not updated automatically"

ALTER TABLE report_definition ADD COLUMN last_calculated_at TIMESTAMP;
ALTER TABLE report_definition ADD COLUMN cached_result JSONB;

COMMENT ON COLUMN report_definition.last_calculated_at IS '上次计算完成时间';
COMMENT ON COLUMN report_definition.cached_result IS '缓存的计算结果（JSON 格式，对应 ReportExecuteResultVO）';

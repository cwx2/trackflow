-- V176__project_name_unique_index.sql
-- 为 project 表的 name 列添加大小写不敏感的唯一索引
-- 对标 YouTrack: "The project name must be unique."
-- 参考: https://www.jetbrains.com/help/youtrack/server/configuring-a-project.html

CREATE UNIQUE INDEX idx_project_name_unique ON project(LOWER(name));

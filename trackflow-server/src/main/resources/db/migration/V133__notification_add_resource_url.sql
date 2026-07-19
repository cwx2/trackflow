-- V133: Add resource_url column to notification table
-- Stores the frontend route path for navigation from notification click and email links.
-- Relative path (e.g. /issues/DE4-123) stored at notify time, so front-end can navigate directly.

ALTER TABLE notification
    ADD COLUMN resource_url VARCHAR(500) NULL;

COMMENT ON COLUMN notification.resource_url IS '资源直链路径（前端路由相对路径），用于站内通知点击跳转和邮件链接';

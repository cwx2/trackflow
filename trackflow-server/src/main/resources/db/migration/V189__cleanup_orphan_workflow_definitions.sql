-- V189__cleanup_orphan_workflow_definitions.sql
-- 清理项目删除后遗留的孤立工作流定义（is_default=false 且没有任何项目绑定）
-- 这些孤立数据是因为 ProjectService.deleteProject() 缺少 WorkflowDefinition 清理逻辑而积累的

DELETE FROM workflow_definition
WHERE is_default = false
  AND id NOT IN (
      SELECT DISTINCT workflow_definition_id FROM project_workflow
  );

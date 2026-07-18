package com.trackflow.common.event;

/**
 * 项目可见性变更事件。
 * <p>
 * 当项目 visibility 字段被修改时发布（如 private → internal），
 * 由 {@link com.trackflow.project.service.ProjectCacheEventListener} 监听并在事务提交后
 * 清除所有用户的 accessible_projects 缓存。
 * <p>
 * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保：
 * 1. 事务成功后才清缓存，避免回滚时误清
 * 2. Redis SCAN 操作不阻塞 DB 事务，释放数据库连接
 *
 * @param projectId     变更的项目 ID
 * @param oldVisibility 旧可见性值
 * @param newVisibility 新可见性值
 */
public record ProjectVisibilityChangedEvent(Long projectId, String oldVisibility, String newVisibility) {
}

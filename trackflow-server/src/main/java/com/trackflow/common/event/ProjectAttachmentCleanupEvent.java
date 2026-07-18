package com.trackflow.common.event;

import java.util.List;

/**
 * 项目附件清理事件。
 * <p>
 * 在项目删除事务内发布（此时 issue_attachment 记录仍可查询），
 * 由 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 监听器在事务成功提交后执行 MinIO 文件删除。
 * <p>
 * 这确保了：
 * - 如果 DB 事务回滚，MinIO 文件不会被误删
 * - 如果 MinIO 删除失败，不影响 DB 事务的成功状态
 *
 * @param projectId 被删除的项目 ID（用于日志）
 * @param filePaths 需要从 MinIO 删除的文件路径列表
 */
public record ProjectAttachmentCleanupEvent(Long projectId, List<String> filePaths) {
}

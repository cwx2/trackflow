package com.trackflow.project.service;

import com.trackflow.common.event.ProjectAttachmentCleanupEvent;
import com.trackflow.common.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 项目附件清理事件监听器。
 * <p>
 * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保 MinIO 文件删除仅在 DB 事务成功提交后执行。
 * 这避免了事务回滚时文件已被不可逆删除的数据不一致问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectAttachmentCleanupListener {

    private final MinioService minioService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAttachmentCleanup(ProjectAttachmentCleanupEvent event) {
        var filePaths = event.filePaths();
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }

        Long projectId = event.projectId();
        log.info("Cleaning up {} MinIO attachments for project {} (post-commit)", filePaths.size(), projectId);

        int successCount = 0;
        int failCount = 0;
        for (String filePath : filePaths) {
            try {
                minioService.delete(filePath);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to delete MinIO object during project cleanup: {}", filePath, e);
            }
        }

        if (failCount > 0) {
            log.warn("Project {} attachment cleanup: {} succeeded, {} failed (orphaned in MinIO)",
                    projectId, successCount, failCount);
        } else {
            log.info("Project {} attachment cleanup completed: {} files deleted", projectId, successCount);
        }
    }
}

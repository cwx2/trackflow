package com.trackflow.external.common;

import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.issue.entity.Issue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 外部集成事件桥接器。
 * <p>
 * 监听核心模块的 Spring Event（如 IssueNotificationEvent），
 * 将其转化为 ExternalEvent 并通过 ExternalEventPublisher 分发到各适配器。
 * <p>
 * 设计要点：
 * - 核心模块不 import 任何 external 类（单向依赖：external → core）
 * - 使用 @TransactionalEventListener(AFTER_COMMIT) 确保事务提交后才触发外部调用
 * - 使用 @Async 异步执行，避免阻塞核心业务流程
 * - 任何适配器失败不会影响核心事务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalEventBridge {

    private final ExternalEventPublisher eventPublisher;

    /**
     * 监听工单创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(IssueNotificationEvent.Created event) {
        Issue issue = event.issue();
        String payload = buildIssuePayload(issue, "created");
        eventPublisher.publish(IssueExternalEvent.created(issue.getIssueKey(), payload));
    }

    /**
     * 监听工单状态变更事件。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueStatusChanged(IssueNotificationEvent.StatusChanged event) {
        Issue issue = event.issue();
        String payload = String.format(
                "{\"issueKey\":\"%s\",\"oldStatusId\":%d,\"newStatusId\":%d,\"operatorId\":%d}",
                issue.getIssueKey(), event.oldStatusId(), event.newStatusId(), event.operatorId()
        );
        eventPublisher.publish(IssueExternalEvent.statusChanged(issue.getIssueKey(), payload));
    }

    /**
     * 监听工单取消事件（状态转换到 cancelled 类别时触发）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCancelled(IssueNotificationEvent.Cancelled event) {
        Issue issue = event.issue();
        String payload = String.format(
                "{\"issueKey\":\"%s\",\"projectId\":%d,\"operatorId\":%d,\"action\":\"cancelled\"}",
                issue.getIssueKey(), issue.getProjectId(), event.operatorId()
        );
        eventPublisher.publish(IssueExternalEvent.cancelled(issue.getIssueKey(), payload));
    }

    /**
     * 监听工单分配事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueAssigned(IssueNotificationEvent.Assigned event) {
        Issue issue = event.issue();
        String payload = String.format(
                "{\"issueKey\":\"%s\",\"assigneeId\":%d,\"operatorId\":%d}",
                issue.getIssueKey(), event.assigneeId(), event.operatorId()
        );
        eventPublisher.publish(IssueExternalEvent.assigned(issue.getIssueKey(), payload));
    }

    /**
     * 监听工单评论事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCommented(IssueNotificationEvent.Commented event) {
        Issue issue = event.issue();
        String payload = String.format(
                "{\"issueKey\":\"%s\",\"commenterId\":%d}",
                issue.getIssueKey(), event.commenterId()
        );
        eventPublisher.publish(IssueExternalEvent.commentCreated(issue.getIssueKey(), payload));
    }

    /**
     * 构建 Issue 基础 payload JSON
     */
    private String buildIssuePayload(Issue issue, String action) {
        return String.format(
                "{\"issueKey\":\"%s\",\"projectId\":%d,\"title\":\"%s\",\"action\":\"%s\"}",
                issue.getIssueKey(),
                issue.getProjectId(),
                escapeJson(issue.getTitle()),
                action
        );
    }

    /**
     * 简单的 JSON 转义（生产环境应使用 Jackson）
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

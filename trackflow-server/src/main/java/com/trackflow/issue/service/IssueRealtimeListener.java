package com.trackflow.issue.service;

import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.IssueRealtimeEvent;
import com.trackflow.issue.entity.Issue;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Issue 实时推送监听器 — 监听已有的 IssueNotificationEvent，转换为 WebSocket 消息推送。
 * <p>
 * 推送 topic 规则：
 * - 项目级: /topic/projects/{projectId}/issues  （列表页订阅）
 * - 工单级: /topic/issues/{issueId}            （详情页订阅）
 * <p>
 * 使用 @TransactionalEventListener(AFTER_COMMIT) 确保仅在事务提交后推送。
 * 异步执行，不影响主业务事务性能。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueRealtimeListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final SysUserMapper sysUserMapper;

    /**
     * 工单创建事件 → 推送到项目 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(IssueNotificationEvent.Created event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.creatorId());

        IssueRealtimeEvent msg = IssueRealtimeEvent.created(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.creatorId(), operatorName);

        sendToProjectTopic(issue.getProjectId(), msg);
    }

    /**
     * 工单分配事件 → 推送到项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueAssigned(IssueNotificationEvent.Assigned event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        Map<String, Object> changes = new HashMap<>();
        changes.put("assigneeId", event.assigneeId() != null ? event.assigneeId().toString() : null);
        changes.put("assigneeName", getDisplayName(event.assigneeId()));

        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), operatorName);

        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 工单状态变更事件 → 推送到项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(IssueNotificationEvent.StatusChanged event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        Map<String, Object> changes = new HashMap<>();
        changes.put("statusId", event.newStatusId() != null ? event.newStatusId().toString() : null);

        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), operatorName);

        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 工单多字段变更事件 → 推送到项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMultiFieldUpdated(IssueNotificationEvent.MultiFieldUpdated event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        // 将 String[] changes 转为 Object map（前端只需最新值）
        Map<String, Object> changes = new HashMap<>();
        for (Map.Entry<String, String[]> entry : event.changes().entrySet()) {
            String[] vals = entry.getValue();
            changes.put(entry.getKey(), vals.length > 1 ? vals[1] : null);
        }

        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), operatorName);

        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 单字段变更事件（标签等） → 推送到项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFieldUpdated(IssueNotificationEvent.FieldUpdated event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        Map<String, Object> changes = new HashMap<>();
        changes.put(event.fieldName(), event.newValue());

        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), operatorName);

        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 评论事件 → 推送到工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommented(IssueNotificationEvent.Commented event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.commenterId());

        IssueRealtimeEvent msg = IssueRealtimeEvent.commentAdded(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.commenterId(), operatorName);

        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 附件事件 → 推送到工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentAdded(IssueNotificationEvent.AttachmentAdded event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        Map<String, Object> changes = Map.of("fileName", event.fileName());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.ATTACHMENT_CHANGED, changes,
                event.operatorId(), operatorName, LocalDateTime.now());

        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 关联变更 → 推送到工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinkChanged(IssueNotificationEvent.LinkChanged event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        Map<String, Object> changes = Map.of(
                "targetIssueKey", event.targetIssueKey(),
                "linkType", event.linkType(),
                "added", event.added()
        );
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.LINK_CHANGED, changes,
                event.operatorId(), operatorName, LocalDateTime.now());

        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 工单删除事件 → 推送到项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueDeleted(IssueNotificationEvent.Deleted event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        IssueRealtimeEvent msg = IssueRealtimeEvent.deleted(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.operatorId(), operatorName);

        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    // ========== 工具方法 ==========

    private void sendToProjectTopic(Long projectId, IssueRealtimeEvent event) {
        String destination = "/topic/projects/" + projectId + "/issues";
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("[WebSocket] 推送到 {}: issueKey={}, action={}",
                    destination, event.issueKey(), event.action());
        } catch (Exception e) {
            log.warn("[WebSocket] 推送失败 {}: {}", destination, e.getMessage());
        }
    }

    private void sendToIssueTopic(Long issueId, IssueRealtimeEvent event) {
        String destination = "/topic/issues/" + issueId;
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("[WebSocket] 推送到 {}: action={}", destination, event.action());
        } catch (Exception e) {
            log.warn("[WebSocket] 推送失败 {}: {}", destination, e.getMessage());
        }
    }

    private String getDisplayName(Long userId) {
        if (userId == null) return null;
        try {
            var user = sysUserMapper.selectById(userId);
            return user != null ? user.getDisplayName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

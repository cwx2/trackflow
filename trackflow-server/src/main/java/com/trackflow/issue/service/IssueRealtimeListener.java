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
 * Issue 实时推送监听器 — 监听 IssueNotificationEvent，转换为 WebSocket 消息推送给前端。
 *
 * <p>推送 topic 规则：
 * <ul>
 *   <li>项目级: {@code /topic/projects/{projectId}/issues} — 列表页/看板/Sprint 页订阅</li>
 *   <li>工单级: {@code /topic/issues/{issueId}} — 工单详情页订阅</li>
 * </ul>
 *
 * <p>使用 {@code @TransactionalEventListener(AFTER_COMMIT)} 确保仅在事务提交后推送，
 * 避免推送后事务回滚导致前端显示脏数据。
 * 异步执行，不阻塞主业务事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueRealtimeListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final SysUserMapper sysUserMapper;

    // ========== 工单生命周期 ==========

    /**
     * 工单创建 → 项目 topic（列表页新增行）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(IssueNotificationEvent.Created event) {
        Issue issue = event.issue();
        IssueRealtimeEvent msg = IssueRealtimeEvent.created(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.creatorId(), getDisplayName(event.creatorId()));
        sendToProjectTopic(issue.getProjectId(), msg);
    }

    /**
     * 工单删除 → 项目 topic + 工单 topic（列表移除行 + 详情页跳出）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueDeleted(IssueNotificationEvent.Deleted event) {
        Issue issue = event.issue();
        IssueRealtimeEvent msg = IssueRealtimeEvent.deleted(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.operatorId(), getDisplayName(event.operatorId()));
        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 工单从回收站恢复 → 项目 topic（列表页重新出现）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueRestored(IssueNotificationEvent.Restored event) {
        Issue issue = event.issue();
        // 用 CREATED action 通知前端：有一条工单重新出现在项目中
        IssueRealtimeEvent msg = IssueRealtimeEvent.created(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.operatorId(), getDisplayName(event.operatorId()));
        sendToProjectTopic(issue.getProjectId(), msg);
    }

    // ========== 字段变更 ==========

    /**
     * 工单分配变更 → 项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueAssigned(IssueNotificationEvent.Assigned event) {
        Issue issue = event.issue();
        Map<String, Object> changes = new HashMap<>();
        changes.put("assigneeId", event.assigneeId() != null ? event.assigneeId().toString() : null);
        changes.put("assigneeName", getDisplayName(event.assigneeId()));
        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), getDisplayName(event.operatorId()));
        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 状态变更 → 项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(IssueNotificationEvent.StatusChanged event) {
        Issue issue = event.issue();
        Map<String, Object> changes = new HashMap<>();
        changes.put("statusId", event.newStatusId() != null ? event.newStatusId().toString() : null);
        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), getDisplayName(event.operatorId()));
        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 多字段批量变更（IssueService.update）→ 项目 topic + 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMultiFieldUpdated(IssueNotificationEvent.MultiFieldUpdated event) {
        Issue issue = event.issue();
        Map<String, Object> changes = new HashMap<>();
        for (Map.Entry<String, String[]> entry : event.changes().entrySet()) {
            String[] vals = entry.getValue();
            changes.put(entry.getKey(), vals.length > 1 ? vals[1] : null);
        }
        IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                changes, event.operatorId(), getDisplayName(event.operatorId()));
        sendToProjectTopic(issue.getProjectId(), msg);
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 单字段变更（IssueTagService 等）→ 根据字段名决定推送范围和 action。
     *
     * <p>标签变更（fieldName="tags"）使用 TAG_CHANGED action，其余使用 FIELD_UPDATED。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFieldUpdated(IssueNotificationEvent.FieldUpdated event) {
        Issue issue = event.issue();
        String operatorName = getDisplayName(event.operatorId());

        if ("tags".equals(event.fieldName())) {
            // 标签变更：使用专属 action，前端据此更新标签列表
            Map<String, Object> changes = new HashMap<>();
            changes.put("oldTag", event.oldValue());
            changes.put("newTag", event.newValue());
            IssueRealtimeEvent msg = new IssueRealtimeEvent(
                    issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                    IssueRealtimeEvent.Action.TAG_CHANGED, changes,
                    event.operatorId(), operatorName, LocalDateTime.now());
            sendToIssueTopic(issue.getId(), msg);
            sendToProjectTopic(issue.getProjectId(), msg);
        } else {
            Map<String, Object> changes = new HashMap<>();
            changes.put(event.fieldName(), event.newValue());
            IssueRealtimeEvent msg = IssueRealtimeEvent.fieldUpdated(
                    issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                    changes, event.operatorId(), operatorName);
            sendToProjectTopic(issue.getProjectId(), msg);
            sendToIssueTopic(issue.getId(), msg);
        }
    }

    // ========== 评论 ==========

    /**
     * 评论新增 → 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommented(IssueNotificationEvent.Commented event) {
        Issue issue = event.issue();
        IssueRealtimeEvent msg = IssueRealtimeEvent.commentAdded(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                event.commenterId(), getDisplayName(event.commenterId()));
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 评论删除 → 工单 topic
     * 前端据 commentId 将对应评论标记为已删除状态
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentDeleted(IssueNotificationEvent.CommentDeleted event) {
        Issue issue = event.issue();
        Map<String, Object> changes = Map.of("commentId", event.commentId().toString());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.COMMENT_DELETED, changes,
                event.operatorId(), getDisplayName(event.operatorId()), LocalDateTime.now());
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 评论还原 → 工单 topic
     * 前端据 commentId 重新显示对应评论
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentRestored(IssueNotificationEvent.CommentRestored event) {
        Issue issue = event.issue();
        Map<String, Object> changes = Map.of("commentId", event.commentId().toString());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.COMMENT_RESTORED, changes,
                event.operatorId(), getDisplayName(event.operatorId()), LocalDateTime.now());
        sendToIssueTopic(issue.getId(), msg);
    }

    // ========== 附件 / 关联 ==========

    /**
     * 附件变更 → 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentAdded(IssueNotificationEvent.AttachmentAdded event) {
        Issue issue = event.issue();
        Map<String, Object> changes = Map.of("fileName", event.fileName());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.ATTACHMENT_CHANGED, changes,
                event.operatorId(), getDisplayName(event.operatorId()), LocalDateTime.now());
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 关联变更 → 工单 topic
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinkChanged(IssueNotificationEvent.LinkChanged event) {
        Issue issue = event.issue();
        Map<String, Object> changes = Map.of(
                "targetIssueKey", event.targetIssueKey(),
                "linkType", event.linkType(),
                "added", event.added());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.LINK_CHANGED, changes,
                event.operatorId(), getDisplayName(event.operatorId()), LocalDateTime.now());
        sendToIssueTopic(issue.getId(), msg);
    }

    // ========== 工时 / 投票 ==========

    /**
     * 工时记录 → 工单 topic（前端刷新工时区域）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimeLogged(IssueNotificationEvent.TimeLogged event) {
        Issue issue = event.issue();
        Map<String, Object> changes = Map.of("durationMinutes", event.durationMinutes());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.FIELD_UPDATED, changes,
                event.operatorId(), getDisplayName(event.operatorId()), LocalDateTime.now());
        sendToIssueTopic(issue.getId(), msg);
    }

    /**
     * 投票 → 工单 topic + 项目 topic（前端更新投票数）
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoted(IssueNotificationEvent.Voted event) {
        Issue issue = event.issue();
        Map<String, Object> changes = Map.of("voteCount", event.voteCount());
        IssueRealtimeEvent msg = new IssueRealtimeEvent(
                issue.getId(), issue.getProjectId(), issue.getIssueKey(),
                IssueRealtimeEvent.Action.FIELD_UPDATED, changes,
                event.voterId(), getDisplayName(event.voterId()), LocalDateTime.now());
        sendToIssueTopic(issue.getId(), msg);
        sendToProjectTopic(issue.getProjectId(), msg);
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

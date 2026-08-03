package com.trackflow.common.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Issue 实时变更事件 — 通过 WebSocket 推送到前端。
 * <p>
 * 与 {@link IssueNotificationEvent} 的区别：
 * - IssueNotificationEvent 用于触发通知（邮件/站内信），面向被通知人
 * - IssueRealtimeEvent 用于实时 UI 更新，面向所有正在查看该数据的用户
 *
 * @param issueId       变更的工单 ID
 * @param projectId     工单所属项目 ID（用于 topic 路由）
 * @param issueKey      工单 key（如 DE4-123）
 * @param action        变更动作类型
 * @param changes       变更的字段 → [旧值, 新值]
 * @param operatorId    操作者 ID（前端据此判断是否为自己的操作，避免重复更新）
 * @param operatorName  操作者显示名
 * @param timestamp     变更时间
 */
public record IssueRealtimeEvent(
        Long issueId,
        Long projectId,
        String issueKey,
        Action action,
        Map<String, Object> changes,
        Long operatorId,
        String operatorName,
        LocalDateTime timestamp
) {

    /**
     * 变更动作类型
     */
    public enum Action {
        /** 字段更新（状态、优先级、负责人、Sprint 等） */
        FIELD_UPDATED,
        /** 评论新增 */
        COMMENT_ADDED,
        /** 评论删除（软删除） */
        COMMENT_DELETED,
        /** 评论还原 */
        COMMENT_RESTORED,
        /** 标签变更 */
        TAG_CHANGED,
        /** 附件变更 */
        ATTACHMENT_CHANGED,
        /** 关联变更 */
        LINK_CHANGED,
        /** 工单创建 */
        CREATED,
        /** 工单删除 */
        DELETED
    }

    /**
     * 构建字段更新事件
     */
    public static IssueRealtimeEvent fieldUpdated(Long issueId, Long projectId, String issueKey,
                                                   Map<String, Object> changes,
                                                   Long operatorId, String operatorName) {
        return new IssueRealtimeEvent(issueId, projectId, issueKey,
                Action.FIELD_UPDATED, changes, operatorId, operatorName, LocalDateTime.now());
    }

    /**
     * 构建评论新增事件
     */
    public static IssueRealtimeEvent commentAdded(Long issueId, Long projectId, String issueKey,
                                                   Long operatorId, String operatorName) {
        return new IssueRealtimeEvent(issueId, projectId, issueKey,
                Action.COMMENT_ADDED, Map.of(), operatorId, operatorName, LocalDateTime.now());
    }

    /**
     * 构建工单创建事件
     */
    public static IssueRealtimeEvent created(Long issueId, Long projectId, String issueKey,
                                              Long operatorId, String operatorName) {
        return new IssueRealtimeEvent(issueId, projectId, issueKey,
                Action.CREATED, Map.of(), operatorId, operatorName, LocalDateTime.now());
    }

    /**
     * 构建工单删除事件
     */
    public static IssueRealtimeEvent deleted(Long issueId, Long projectId, String issueKey,
                                              Long operatorId, String operatorName) {
        return new IssueRealtimeEvent(issueId, projectId, issueKey,
                Action.DELETED, Map.of(), operatorId, operatorName, LocalDateTime.now());
    }
}

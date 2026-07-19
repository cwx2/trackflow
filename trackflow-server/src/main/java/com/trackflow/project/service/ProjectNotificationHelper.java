package com.trackflow.project.service;

import com.trackflow.common.notification.AbstractNotificationHelper;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationOutboxWriter;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.project.mapper.ProjectMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 项目通知助手：负责在项目关键操作后向相关人员推送站内通知。
 * <p>
 * 通知规则：
 * - 排除当前操作者（不通知自己）
 * - 尊重用户 NotificationPreference 中的项目事件订阅开关
 * - 项目删除为强制通知（不受偏好控制，因为是不可逆操作）
 * - 通知创建失败不影响主流程（catch + log）
 * <p>
 * 所有公共方法标记 @Async("notificationExecutor")，在独立线程池中执行，
 * 不阻塞主请求线程。
 */
@Slf4j
@Component
public class ProjectNotificationHelper extends AbstractNotificationHelper {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationOutboxWriter outboxWriter;

    public ProjectNotificationHelper(NotificationService notificationService,
                                     NotificationPreferenceService preferenceService,
                                     NotificationOutboxWriter outboxWriter,
                                     ProjectMemberMapper memberMapper) {
        super(null, memberMapper); // ProjectNotificationHelper 不需要 SysUserMapper
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.outboxWriter = outboxWriter;
    }

    // ==================== 成员变更通知 ====================

    /**
     * 通知用户被添加到项目
     */
    @Async("notificationExecutor")
    public void notifyMemberAdded(Long userId, Long operatorId, Long projectId,
                                  String projectName, String roleNames) {
        if (userId.equals(operatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(userId, NotificationEventType.PROJECT_MEMBER_CHANGED, projectId)) {
                return;
            }
            String title = "你已被添加到项目";
            String content = String.format("你已被添加到项目「%s」，角色为「%s」", projectName, roleNames);
            notificationService.notify(userId, operatorId, title, content,
                    NotificationType.member_added, NotificationReason.member,
                    "project", projectId, projectId);
            log.debug("[ProjectNotification] 成员添加通知已发送: project={}, user={}", projectId, userId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送成员添加通知失败: project={}, user={}, error={}",
                    projectId, userId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyMemberAdded", e, outboxWriter.buildNotifyParams(
                    userId, operatorId, "你已被添加到项目",
                    String.format("你已被添加到项目「%s」，角色为「%s」", projectName, roleNames),
                    NotificationType.member_added.name(), NotificationReason.member.name(),
                    "project", projectId, projectId));
        }
    }

    /**
     * 通知用户角色变更
     */
    @Async("notificationExecutor")
    public void notifyRoleChanged(Long userId, Long operatorId, Long projectId,
                                  String projectName, String newRoleNames) {
        if (userId.equals(operatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(userId, NotificationEventType.PROJECT_MEMBER_CHANGED, projectId)) {
                return;
            }
            String title = "你的项目角色已变更";
            String content = String.format("你在项目「%s」中的角色已变更为「%s」", projectName, newRoleNames);
            notificationService.notify(userId, operatorId, title, content,
                    NotificationType.role_changed, NotificationReason.member,
                    "project", projectId, projectId);
            log.debug("[ProjectNotification] 角色变更通知已发送: project={}, user={}", projectId, userId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送角色变更通知失败: project={}, user={}, error={}",
                    projectId, userId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyRoleChanged", e, outboxWriter.buildNotifyParams(
                    userId, operatorId, "你的项目角色已变更",
                    String.format("你在项目「%s」中的角色已变更为「%s」", projectName, newRoleNames),
                    NotificationType.role_changed.name(), NotificationReason.member.name(),
                    "project", projectId, projectId));
        }
    }

    /**
     * 通知用户被移出项目
     */
    @Async("notificationExecutor")
    public void notifyMemberRemoved(Long userId, Long operatorId, Long projectId,
                                    String projectName) {
        if (userId.equals(operatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(userId, NotificationEventType.PROJECT_MEMBER_CHANGED, projectId)) {
                return;
            }
            String title = "你已被移出项目";
            String content = String.format("你已被移出项目「%s」", projectName);
            notificationService.notify(userId, operatorId, title, content,
                    NotificationType.member_removed, NotificationReason.member,
                    "project", projectId, projectId);
            log.debug("[ProjectNotification] 成员移除通知已发送: project={}, user={}", projectId, userId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送成员移除通知失败: project={}, user={}, error={}",
                    projectId, userId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyMemberRemoved", e, outboxWriter.buildNotifyParams(
                    userId, operatorId, "你已被移出项目",
                    String.format("你已被移出项目「%s」", projectName),
                    NotificationType.member_removed.name(), NotificationReason.member.name(),
                    "project", projectId, projectId));
        }
    }

    // ==================== 负责人变更通知 ====================

    /**
     * 通知新负责人
     */
    @Async("notificationExecutor")
    public void notifyNewLead(Long newLeadId, Long operatorId, Long projectId,
                              String projectName) {
        if (newLeadId.equals(operatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(newLeadId, NotificationEventType.PROJECT_MEMBER_CHANGED, projectId)) {
                return;
            }
            String title = "你已成为项目负责人";
            String content = String.format("你已成为项目「%s」的负责人", projectName);
            notificationService.notify(newLeadId, operatorId, title, content,
                    NotificationType.lead_changed, NotificationReason.assigned,
                    "project", projectId, projectId);
            log.debug("[ProjectNotification] 新负责人通知已发送: project={}, newLead={}", projectId, newLeadId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送新负责人通知失败: project={}, newLead={}, error={}",
                    projectId, newLeadId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyNewLead", e, outboxWriter.buildNotifyParams(
                    newLeadId, operatorId, "你已成为项目负责人",
                    String.format("你已成为项目「%s」的负责人", projectName),
                    NotificationType.lead_changed.name(), NotificationReason.assigned.name(),
                    "project", projectId, projectId));
        }
    }

    /**
     * 通知旧负责人
     */
    @Async("notificationExecutor")
    public void notifyOldLead(Long oldLeadId, Long operatorId, Long projectId,
                              String projectName, String newLeadName) {
        if (oldLeadId.equals(operatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(oldLeadId, NotificationEventType.PROJECT_MEMBER_CHANGED, projectId)) {
                return;
            }
            String title = "项目负责人已变更";
            String content = String.format("项目「%s」的负责人已变更为「%s」", projectName, newLeadName);
            notificationService.notify(oldLeadId, operatorId, title, content,
                    NotificationType.lead_changed, NotificationReason.member,
                    "project", projectId, projectId);
            log.debug("[ProjectNotification] 旧负责人通知已发送: project={}, oldLead={}", projectId, oldLeadId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送旧负责人通知失败: project={}, oldLead={}, error={}",
                    projectId, oldLeadId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyOldLead", e, outboxWriter.buildNotifyParams(
                    oldLeadId, operatorId, "项目负责人已变更",
                    String.format("项目「%s」的负责人已变更为「%s」", projectName, newLeadName),
                    NotificationType.lead_changed.name(), NotificationReason.member.name(),
                    "project", projectId, projectId));
        }
    }

    // ==================== 项目生命周期通知 ====================

    /**
     * 通知所有成员：项目归档/恢复（受偏好控制）。
     * 使用批量通知接口，N 个成员仅需 2-3 次 DB 操作。
     */
    @Async("notificationExecutor")
    public void notifyLifecycleEvent(Long projectId, Long operatorId,
                                     String title, String content, NotificationType type) {
        try {
            List<Long> memberUserIds = getProjectMemberUserIds(projectId);

            // 排除操作者
            List<Long> recipients = memberUserIds.stream()
                    .filter(id -> !id.equals(operatorId))
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }

            // 批量偏好过滤
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipients, NotificationEventType.PROJECT_LIFECYCLE, projectId);
            if (enabledUserIds.isEmpty()) {
                return;
            }

            notificationService.notifyBatch(enabledUserIds, operatorId, title, content,
                    type, "project", projectId, projectId);

            log.debug("[ProjectNotification] 生命周期通知已发送: project={}, type={}, sent={}",
                    projectId, type, enabledUserIds.size());
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送生命周期通知失败: project={}, type={}, error={}",
                    projectId, type, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyLifecycleEvent", e, outboxWriter.buildNotifyParams(
                    null, operatorId, title, content,
                    type.name(), null,
                    "project", projectId, projectId));
        }
    }

    /**
     * 通知所有成员：项目删除（强制通知，不受偏好控制——不可逆操作）。
     * 使用批量通知接口，N 个成员仅需 2-3 次 DB 操作。
     */
    @Async("notificationExecutor")
    public void notifyProjectDeleted(Long projectId, List<Long> memberUserIds, Long operatorId,
                                     String projectName, String projectKey) {
        try {
            String title = "项目已被删除";
            String content = String.format("项目「%s」(%s) 已被删除，相关工单和数据已清除。",
                    projectName, projectKey);

            // 排除操作者（项目删除为强制通知，不检查偏好）
            List<Long> recipients = memberUserIds.stream()
                    .filter(id -> !id.equals(operatorId))
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }

            notificationService.notifyBatch(recipients, operatorId, title, content,
                    NotificationType.project_deleted, "project", projectId, projectId);

            log.debug("[ProjectNotification] 项目删除通知已发送: project={}, sent={}", projectId, recipients.size());
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送项目删除通知失败: project={}, error={}",
                    projectId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyProjectDeleted", e, outboxWriter.buildNotifyParams(
                    null, operatorId, "项目已被删除",
                    String.format("项目「%s」(%s) 已被删除", projectName, projectKey),
                    NotificationType.project_deleted.name(), null,
                    "project", projectId, projectId));
        }
    }

}

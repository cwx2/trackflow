package com.trackflow.sprint.service;

import com.trackflow.common.notification.AbstractNotificationHelper;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationOutboxWriter;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Sprint 通知助手：负责在 Sprint 生命周期事件后向项目成员推送站内通知。
 * <p>
 * 通知规则：
 * - 通知 Sprint 所属项目的所有成员（排除操作者本人）
 * - 尊重用户 NotificationPreference 中的 onSprintStarted / onSprintCompleted 开关
 * - 通知创建失败不影响主流程（catch + log）
 * <p>
 * 所有公共方法标记 @Async("notificationExecutor")，在独立线程池中执行。
 */
@Slf4j
@Component
public class SprintNotificationHelper extends AbstractNotificationHelper {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationOutboxWriter outboxWriter;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public SprintNotificationHelper(NotificationService notificationService,
                                    NotificationPreferenceService preferenceService,
                                    NotificationOutboxWriter outboxWriter,
                                    ProjectMemberMapper projectMemberMapper,
                                    SysUserMapper sysUserMapper) {
        super(sysUserMapper, projectMemberMapper);
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Sprint 激活通知：通知项目所有成员（排除操作者）。
     * 使用批量通知接口，N 个成员仅需 2-3 次 DB 操作。
     *
     * @param sprint     被激活的 Sprint
     * @param operatorId 执行操作的用户 ID
     */
    @Async("notificationExecutor")
    public void notifySprintActivated(Sprint sprint, Long operatorId) {
        try {
            List<Long> memberUserIds = getProjectMemberUserIds(sprint.getProjectId());
            if (memberUserIds.isEmpty()) {
                return;
            }

            // 排除操作者
            List<Long> recipients = memberUserIds.stream()
                    .filter(id -> !id.equals(operatorId))
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }

            // 批量偏好过滤
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipients, NotificationEventType.SPRINT_STARTED, sprint.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String operatorName = getUserDisplayName(operatorId);
            String dateRange = formatDateRange(sprint);
            String title = String.format("Sprint「%s」已启动", sprint.getName());
            String content = String.format("%s 启动了 Sprint「%s」，周期：%s",
                    operatorName, sprint.getName(), dateRange);

            notificationService.notifyBatch(enabledUserIds, operatorId, title, content,
                    NotificationType.sprint_started, "sprint", sprint.getId(), sprint.getProjectId());

            log.debug("[SprintNotification] 已发送Sprint激活通知: sprint={}, recipients={}",
                    sprint.getName(), enabledUserIds.size());
        } catch (Exception e) {
            log.error("[SprintNotification] 发送Sprint激活通知失败: sprint={}, error={}",
                    sprint.getName(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifySprintActivated", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("Sprint「%s」已启动", sprint.getName()),
                    String.format("Sprint「%s」已启动", sprint.getName()),
                    NotificationType.sprint_started.name(), null,
                    "sprint", sprint.getId(), sprint.getProjectId()));
        }
    }

    /**
     * Sprint 完成通知：通知项目所有成员（排除操作者）。
     * 使用批量通知接口，N 个成员仅需 2-3 次 DB 操作。
     *
     * @param sprint          被完成的 Sprint
     * @param completedIssues 已完成的工单数量
     * @param operatorId      执行操作的用户 ID
     */
    @Async("notificationExecutor")
    public void notifySprintCompleted(Sprint sprint, int completedIssues, Long operatorId) {
        try {
            List<Long> memberUserIds = getProjectMemberUserIds(sprint.getProjectId());
            if (memberUserIds.isEmpty()) {
                return;
            }

            // 排除操作者
            List<Long> recipients = memberUserIds.stream()
                    .filter(id -> !id.equals(operatorId))
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }

            // 批量偏好过滤
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipients, NotificationEventType.SPRINT_COMPLETED, sprint.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String operatorName = getUserDisplayName(operatorId);
            String title = String.format("Sprint「%s」已完成", sprint.getName());
            String content = String.format("%s 完成了 Sprint「%s」，共完成 %d 个工单",
                    operatorName, sprint.getName(), completedIssues);

            notificationService.notifyBatch(enabledUserIds, operatorId, title, content,
                    NotificationType.sprint_completed, "sprint", sprint.getId(), sprint.getProjectId());

            log.debug("[SprintNotification] 已发送Sprint完成通知: sprint={}, recipients={}",
                    sprint.getName(), enabledUserIds.size());
        } catch (Exception e) {
            log.error("[SprintNotification] 发送Sprint完成通知失败: sprint={}, error={}",
                    sprint.getName(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifySprintCompleted", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("Sprint「%s」已完成", sprint.getName()),
                    String.format("Sprint「%s」已完成，共完成 %d 个工单", sprint.getName(), completedIssues),
                    NotificationType.sprint_completed.name(), null,
                    "sprint", sprint.getId(), sprint.getProjectId()));
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 格式化 Sprint 日期范围
     */
    private String formatDateRange(Sprint sprint) {
        if (sprint.getStartDate() == null && sprint.getEndDate() == null) {
            return "未设置日期";
        }
        String start = sprint.getStartDate() != null ? sprint.getStartDate().format(DATE_FMT) : "?";
        String end = sprint.getEndDate() != null ? sprint.getEndDate().format(DATE_FMT) : "?";
        return start + " — " + end;
    }
}

package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
@RequiredArgsConstructor
public class SprintNotificationHelper {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final ProjectMemberMapper projectMemberMapper;
    private final SysUserMapper sysUserMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Sprint 激活通知：通知项目所有成员（排除操作者）。
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

            String operatorName = getUserDisplayName(operatorId);
            String dateRange = formatDateRange(sprint);
            String title = String.format("Sprint「%s」已启动", sprint.getName());
            String content = String.format("%s 启动了 Sprint「%s」，周期：%s",
                    operatorName, sprint.getName(), dateRange);

            int sent = 0;
            for (Long userId : memberUserIds) {
                if (userId.equals(operatorId)) {
                    continue;
                }
                if (!preferenceService.isEnabled(userId, NotificationEventType.SPRINT_STARTED, sprint.getProjectId())) {
                    continue;
                }
                notificationService.notify(userId, operatorId, title, content,
                        NotificationType.sprint_started, "sprint", sprint.getId(), sprint.getProjectId());
                sent++;
            }
            if (sent > 0) {
                log.debug("[SprintNotification] 已发送Sprint激活通知: sprint={}, recipients={}",
                        sprint.getName(), sent);
            }
        } catch (Exception e) {
            log.error("[SprintNotification] 发送Sprint激活通知失败: sprint={}, error={}",
                    sprint.getName(), e.getMessage(), e);
        }
    }

    /**
     * Sprint 完成通知：通知项目所有成员（排除操作者）。
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

            String operatorName = getUserDisplayName(operatorId);
            String title = String.format("Sprint「%s」已完成", sprint.getName());
            String content = String.format("%s 完成了 Sprint「%s」，共完成 %d 个工单",
                    operatorName, sprint.getName(), completedIssues);

            int sent = 0;
            for (Long userId : memberUserIds) {
                if (userId.equals(operatorId)) {
                    continue;
                }
                if (!preferenceService.isEnabled(userId, NotificationEventType.SPRINT_COMPLETED, sprint.getProjectId())) {
                    continue;
                }
                notificationService.notify(userId, operatorId, title, content,
                        NotificationType.sprint_completed, "sprint", sprint.getId(), sprint.getProjectId());
                sent++;
            }
            if (sent > 0) {
                log.debug("[SprintNotification] 已发送Sprint完成通知: sprint={}, recipients={}",
                        sprint.getName(), sent);
            }
        } catch (Exception e) {
            log.error("[SprintNotification] 发送Sprint完成通知失败: sprint={}, error={}",
                    sprint.getName(), e.getMessage(), e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取项目所有成员的 userId 列表
     */
    private List<Long> getProjectMemberUserIds(Long projectId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .select(ProjectMember::getUserId)
        );
        return members.stream()
                .map(ProjectMember::getUserId)
                .distinct()
                .toList();
    }

    /**
     * 获取用户显示名称
     */
    private String getUserDisplayName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user != null && user.getDisplayName() != null ? user.getDisplayName() : String.valueOf(userId);
        } catch (Exception e) {
            return String.valueOf(userId);
        }
    }

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

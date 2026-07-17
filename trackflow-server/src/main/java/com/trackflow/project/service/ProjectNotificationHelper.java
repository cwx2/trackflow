package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

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
@RequiredArgsConstructor
public class ProjectNotificationHelper {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final ProjectMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;

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
            if (!isPreferenceEnabled(userId, "onProjectMemberChanged")) {
                return;
            }
            String title = "你已被添加到项目";
            String content = String.format("你已被添加到项目「%s」，角色为「%s」", projectName, roleNames);
            notificationService.notify(userId, operatorId, title, content,
                    "member_added", "project", projectId);
            log.debug("[ProjectNotification] 成员添加通知已发送: project={}, user={}", projectId, userId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送成员添加通知失败: project={}, user={}, error={}",
                    projectId, userId, e.getMessage(), e);
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
            if (!isPreferenceEnabled(userId, "onProjectMemberChanged")) {
                return;
            }
            String title = "你的项目角色已变更";
            String content = String.format("你在项目「%s」中的角色已变更为「%s」", projectName, newRoleNames);
            notificationService.notify(userId, operatorId, title, content,
                    "role_changed", "project", projectId);
            log.debug("[ProjectNotification] 角色变更通知已发送: project={}, user={}", projectId, userId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送角色变更通知失败: project={}, user={}, error={}",
                    projectId, userId, e.getMessage(), e);
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
            if (!isPreferenceEnabled(userId, "onProjectMemberChanged")) {
                return;
            }
            String title = "你已被移出项目";
            String content = String.format("你已被移出项目「%s」", projectName);
            notificationService.notify(userId, operatorId, title, content,
                    "member_removed", "project", projectId);
            log.debug("[ProjectNotification] 成员移除通知已发送: project={}, user={}", projectId, userId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送成员移除通知失败: project={}, user={}, error={}",
                    projectId, userId, e.getMessage(), e);
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
            if (!isPreferenceEnabled(newLeadId, "onProjectMemberChanged")) {
                return;
            }
            String title = "你已成为项目负责人";
            String content = String.format("你已成为项目「%s」的负责人", projectName);
            notificationService.notify(newLeadId, operatorId, title, content,
                    "lead_changed", "project", projectId);
            log.debug("[ProjectNotification] 新负责人通知已发送: project={}, newLead={}", projectId, newLeadId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送新负责人通知失败: project={}, newLead={}, error={}",
                    projectId, newLeadId, e.getMessage(), e);
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
            if (!isPreferenceEnabled(oldLeadId, "onProjectMemberChanged")) {
                return;
            }
            String title = "项目负责人已变更";
            String content = String.format("项目「%s」的负责人已变更为「%s」", projectName, newLeadName);
            notificationService.notify(oldLeadId, operatorId, title, content,
                    "lead_changed", "project", projectId);
            log.debug("[ProjectNotification] 旧负责人通知已发送: project={}, oldLead={}", projectId, oldLeadId);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送旧负责人通知失败: project={}, oldLead={}, error={}",
                    projectId, oldLeadId, e.getMessage(), e);
        }
    }

    // ==================== 项目生命周期通知 ====================

    /**
     * 通知所有成员：项目归档/恢复（受偏好控制）
     */
    @Async("notificationExecutor")
    public void notifyLifecycleEvent(Long projectId, Long operatorId,
                                     String title, String content, String type) {
        try {
            List<Long> memberUserIds = getMemberUserIds(projectId);
            int sent = 0;
            for (Long memberId : memberUserIds) {
                if (memberId.equals(operatorId)) {
                    continue;
                }
                if (!isPreferenceEnabled(memberId, "onProjectLifecycle")) {
                    continue;
                }
                notificationService.notify(memberId, operatorId, title, content, type, "project", projectId);
                sent++;
            }
            log.debug("[ProjectNotification] 生命周期通知已发送: project={}, type={}, sent={}",
                    projectId, type, sent);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送生命周期通知失败: project={}, type={}, error={}",
                    projectId, type, e.getMessage(), e);
        }
    }

    /**
     * 通知所有成员：项目删除（强制通知，不受偏好控制——不可逆操作）
     */
    @Async("notificationExecutor")
    public void notifyProjectDeleted(Long projectId, List<Long> memberUserIds, Long operatorId,
                                     String projectName, String projectKey) {
        try {
            String title = "项目已被删除";
            String content = String.format("项目「%s」(%s) 已被删除，相关工单和数据已清除。",
                    projectName, projectKey);
            int sent = 0;
            for (Long memberId : memberUserIds) {
                if (memberId.equals(operatorId)) {
                    continue;
                }
                // 项目删除为强制通知，不检查偏好
                notificationService.notify(memberId, operatorId, title, content,
                        "project_deleted", "project", projectId);
                sent++;
            }
            log.debug("[ProjectNotification] 项目删除通知已发送: project={}, sent={}", projectId, sent);
        } catch (Exception e) {
            log.error("[ProjectNotification] 发送项目删除通知失败: project={}, error={}",
                    projectId, e.getMessage(), e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查用户的项目事件通知偏好是否启用
     */
    private boolean isPreferenceEnabled(Long userId, String preferenceField) {
        try {
            NotificationPreference pref = preferenceService.getByUserId(userId);
            return switch (preferenceField) {
                case "onProjectMemberChanged" -> Boolean.TRUE.equals(pref.getOnProjectMemberChanged());
                case "onProjectLifecycle" -> Boolean.TRUE.equals(pref.getOnProjectLifecycle());
                default -> true;
            };
        } catch (Exception e) {
            log.warn("[ProjectNotification] 查询通知偏好失败: userId={}, 默认发送", userId);
            return true; // 查询失败时默认发送
        }
    }

    /**
     * 获取项目所有成员的 userId 列表
     */
    private List<Long> getMemberUserIds(Long projectId) {
        return memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        ).stream().map(ProjectMember::getUserId).distinct().toList();
    }
}

package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Issue 通知助手：负责在 Issue 关键操作后向相关人员推送站内通知。
 * <p>
 * 通知规则：
 * - 排除当前操作者（不通知自己）
 * - 尊重用户 NotificationPreference 中的事件订阅开关
 * - 通知创建失败不影响主流程（catch + log）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueNotificationHelper {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final IssueCommentMapper commentMapper;
    private final IssueStatusMapper statusMapper;
    private final SysUserMapper sysUserMapper;

    // ==================== 公共通知方法 ====================

    /**
     * 工单被分配时通知被分配人
     */
    public void notifyAssigned(Issue issue, Long assigneeId, Long operatorId) {
        if (assigneeId == null || assigneeId.equals(operatorId)) {
            return;
        }
        try {
            if (!isPreferenceEnabled(assigneeId, "onIssueAssigned")) {
                return;
            }
            String operatorName = getUserDisplayName(operatorId);
            String title = String.format("你被分配了工单 %s", issue.getIssueKey());
            String content = String.format("%s 将工单 [%s] %s 分配给了你",
                    operatorName, issue.getIssueKey(), issue.getTitle());

            notificationService.notify(assigneeId, title, content,
                    "issue_assigned", "issue", issue.getId());
            log.debug("[IssueNotification] 已发送分配通知: issue={}, assignee={}", issue.getIssueKey(), assigneeId);
        } catch (Exception e) {
            log.error("[IssueNotification] 发送分配通知失败: issue={}, assignee={}, error={}",
                    issue.getIssueKey(), assigneeId, e.getMessage(), e);
        }
    }

    /**
     * 新评论通知：通知报告人 + 负责人 + 之前评论者（去重，排除当前用户）
     */
    public void notifyCommented(Issue issue, Long commenterId) {
        try {
            Set<Long> recipients = collectCommentRecipients(issue, commenterId);
            if (recipients.isEmpty()) {
                return;
            }
            String commenterName = getUserDisplayName(commenterId);
            String title = String.format("%s 有新评论", issue.getIssueKey());
            String content = String.format("%s 在工单 [%s] %s 中添加了评论",
                    commenterName, issue.getIssueKey(), issue.getTitle());

            for (Long recipientId : recipients) {
                if (!isPreferenceEnabled(recipientId, "onIssueCommented")) {
                    continue;
                }
                notificationService.notify(recipientId, title, content,
                        "issue_commented", "issue", issue.getId());
            }
            log.debug("[IssueNotification] 已发送评论通知: issue={}, recipients={}",
                    issue.getIssueKey(), recipients.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送评论通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    /**
     * 状态变更通知：通知报告人 + 负责人（去重，排除当前用户）
     */
    public void notifyStatusChanged(Issue issue, Long oldStatusId, Long newStatusId, Long operatorId) {
        try {
            Set<Long> recipients = collectStatusChangeRecipients(issue, operatorId);
            if (recipients.isEmpty()) {
                return;
            }
            String operatorName = getUserDisplayName(operatorId);
            String oldStatusName = getStatusName(oldStatusId);
            String newStatusName = getStatusName(newStatusId);
            String title = String.format("%s 状态变更为 %s", issue.getIssueKey(), newStatusName);
            String content = String.format("%s 将工单 [%s] %s 的状态从「%s」变更为「%s」",
                    operatorName, issue.getIssueKey(), issue.getTitle(), oldStatusName, newStatusName);

            for (Long recipientId : recipients) {
                if (!isPreferenceEnabled(recipientId, "onIssueStatusChanged")) {
                    continue;
                }
                notificationService.notify(recipientId, title, content,
                        "issue_status_changed", "issue", issue.getId());
            }
            log.debug("[IssueNotification] 已发送状态变更通知: issue={}, recipients={}",
                    issue.getIssueKey(), recipients.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送状态变更通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    /**
     * 工单创建时通知被分配人（若指定了 assignee 且不是创建者自己）
     */
    public void notifyCreated(Issue issue, Long creatorId) {
        if (issue.getAssigneeId() == null || issue.getAssigneeId().equals(creatorId)) {
            return;
        }
        try {
            if (!isPreferenceEnabled(issue.getAssigneeId(), "onIssueAssigned")) {
                return;
            }
            String creatorName = getUserDisplayName(creatorId);
            String title = String.format("你被分配了新工单 %s", issue.getIssueKey());
            String content = String.format("%s 创建了工单 [%s] %s 并分配给了你",
                    creatorName, issue.getIssueKey(), issue.getTitle());

            notificationService.notify(issue.getAssigneeId(), title, content,
                    "issue_assigned", "issue", issue.getId());
            log.debug("[IssueNotification] 已发送创建通知: issue={}, assignee={}",
                    issue.getIssueKey(), issue.getAssigneeId());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送创建通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 收集评论通知接收人：报告人 + 负责人 + 之前评论者（去重，排除评论者自己）
     */
    private Set<Long> collectCommentRecipients(Issue issue, Long excludeUserId) {
        Set<Long> recipients = new HashSet<>();

        // 报告人
        if (issue.getReporterId() != null) {
            recipients.add(issue.getReporterId());
        }
        // 负责人
        if (issue.getAssigneeId() != null) {
            recipients.add(issue.getAssigneeId());
        }
        // 之前的评论者
        List<IssueComment> previousComments = commentMapper.selectList(
                new LambdaQueryWrapper<IssueComment>()
                        .eq(IssueComment::getIssueId, issue.getId())
                        .isNull(IssueComment::getDeletedAt)
                        .select(IssueComment::getUserId)
        );
        for (IssueComment c : previousComments) {
            if (c.getUserId() != null) {
                recipients.add(c.getUserId());
            }
        }

        // 排除当前操作者
        recipients.remove(excludeUserId);
        return recipients;
    }

    /**
     * 收集状态变更通知接收人：报告人 + 负责人（去重，排除操作者自己）
     */
    private Set<Long> collectStatusChangeRecipients(Issue issue, Long excludeUserId) {
        Set<Long> recipients = new HashSet<>();
        if (issue.getReporterId() != null) {
            recipients.add(issue.getReporterId());
        }
        if (issue.getAssigneeId() != null) {
            recipients.add(issue.getAssigneeId());
        }
        recipients.remove(excludeUserId);
        return recipients;
    }

    /**
     * 检查用户的通知偏好是否启用某类事件
     */
    private boolean isPreferenceEnabled(Long userId, String preferenceField) {
        try {
            NotificationPreference pref = preferenceService.getByUserId(userId);
            return switch (preferenceField) {
                case "onIssueAssigned" -> Boolean.TRUE.equals(pref.getOnIssueAssigned());
                case "onIssueStatusChanged" -> Boolean.TRUE.equals(pref.getOnIssueStatusChanged());
                case "onIssueCommented" -> Boolean.TRUE.equals(pref.getOnIssueCommented());
                case "onMentioned" -> Boolean.TRUE.equals(pref.getOnMentioned());
                case "onIssueResolved" -> Boolean.TRUE.equals(pref.getOnIssueResolved());
                default -> true;
            };
        } catch (Exception e) {
            log.warn("[IssueNotification] 查询通知偏好失败: userId={}, 默认发送", userId);
            return true; // 查询失败时默认发送
        }
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
     * 获取状态名称
     */
    private String getStatusName(Long statusId) {
        if (statusId == null) {
            return "未知";
        }
        try {
            IssueStatus status = statusMapper.selectById(statusId);
            return status != null ? status.getName() : String.valueOf(statusId);
        } catch (Exception e) {
            return String.valueOf(statusId);
        }
    }
}

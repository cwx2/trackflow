package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue 通知助手：负责在 Issue 关键操作后向相关人员推送站内通知。
 * <p>
 * 通知规则：
 * - 排除当前操作者（不通知自己）
 * - 尊重用户 NotificationPreference 中的事件订阅开关
 * - 通知创建失败不影响主流程（catch + log）
 * <p>
 * 所有公共方法标记 @Async("notificationExecutor")，在独立线程池中执行，
 * 不阻塞主请求线程。调用方无需等待通知发送完成。
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
    @Async("notificationExecutor")
    public void notifyAssigned(Issue issue, Long assigneeId, Long operatorId) {
        if (assigneeId == null || assigneeId.equals(operatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(assigneeId, NotificationEventType.ISSUE_ASSIGNED, issue.getProjectId())) {
                return;
            }
            String operatorName = getUserDisplayName(operatorId);
            String title = String.format("你被分配了工单 %s", issue.getIssueKey());
            String content = String.format("%s 将工单 [%s] %s 分配给了你",
                    operatorName, issue.getIssueKey(), issue.getTitle());

            notificationService.notify(assigneeId, operatorId, title, content,
                    NotificationType.issue_assigned, "issue", issue.getId(), issue.getProjectId());
            log.debug("[IssueNotification] 已发送分配通知: issue={}, assignee={}", issue.getIssueKey(), assigneeId);
        } catch (Exception e) {
            log.error("[IssueNotification] 发送分配通知失败: issue={}, assignee={}, error={}",
                    issue.getIssueKey(), assigneeId, e.getMessage(), e);
        }
    }

    /**
     * 新评论通知：通知报告人 + 负责人 + 之前评论者（去重，排除当前用户）
     */
    @Async("notificationExecutor")
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
                if (!preferenceService.isEnabled(recipientId, NotificationEventType.ISSUE_COMMENTED, issue.getProjectId())) {
                    continue;
                }
                notificationService.notify(recipientId, commenterId, title, content,
                        NotificationType.issue_commented, "issue", issue.getId(), issue.getProjectId());
            }
            log.debug("[IssueNotification] 已发送评论通知: issue={}, recipients={}",
                    issue.getIssueKey(), recipients.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送评论通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    /**
     * 状态变更通知：通知报告人 + 负责人（去重，排除当前用户）。
     * <p>
     * 当新状态属于"已关闭"类别（isClosed=true）时，使用 ISSUE_RESOLVED 偏好检查；
     * 否则使用 ISSUE_STATUS_CHANGED 偏好检查。
     */
    @Async("notificationExecutor")
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

            // 判断新状态是否为"已关闭"类别——若是则使用 ISSUE_RESOLVED 偏好
            NotificationEventType eventType = isClosedStatus(newStatusId)
                    ? NotificationEventType.ISSUE_RESOLVED
                    : NotificationEventType.ISSUE_STATUS_CHANGED;

            for (Long recipientId : recipients) {
                if (!preferenceService.isEnabled(recipientId, eventType, issue.getProjectId())) {
                    continue;
                }
                notificationService.notify(recipientId, operatorId, title, content,
                        NotificationType.issue_status_changed, "issue", issue.getId(), issue.getProjectId());
            }
            log.debug("[IssueNotification] 已发送状态变更通知: issue={}, eventType={}, recipients={}",
                    issue.getIssueKey(), eventType, recipients.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送状态变更通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    /**
     * 工单创建时通知被分配人（若指定了 assignee 且不是创建者自己）
     */
    @Async("notificationExecutor")
    public void notifyCreated(Issue issue, Long creatorId) {
        if (issue.getAssigneeId() == null || issue.getAssigneeId().equals(creatorId)) {
            return;
        }
        try {
            if (!preferenceService.isEnabled(issue.getAssigneeId(), NotificationEventType.ISSUE_ASSIGNED, issue.getProjectId())) {
                return;
            }
            String creatorName = getUserDisplayName(creatorId);
            String title = String.format("你被分配了新工单 %s", issue.getIssueKey());
            String content = String.format("%s 创建了工单 [%s] %s 并分配给了你",
                    creatorName, issue.getIssueKey(), issue.getTitle());

            notificationService.notify(issue.getAssigneeId(), creatorId, title, content,
                    NotificationType.issue_assigned, "issue", issue.getId(), issue.getProjectId());
            log.debug("[IssueNotification] 已发送创建通知: issue={}, assignee={}",
                    issue.getIssueKey(), issue.getAssigneeId());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送创建通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    // ==================== @Mention 通知 ====================

    /**
     * 匹配评论内容中 @username 模式的正则：
     * - @后面跟用户名（字母/数字/下划线/点/连字符）
     * - 支持 HTML 标签之间的 @mention（Tiptap 输出的 HTML 格式）
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z][a-zA-Z0-9._-]{1,49})");

    /**
     * 评论中 @mention 通知：解析评论内容中的 @username，通知被提及的用户。
     * <p>
     * 规则：
     * - 从 HTML 内容中提取纯文本后匹配 @username
     * - 排除评论者自己（不通知自己提及自己）
     * - 排除已经通过评论通知收到通知的用户（由调用方决定是否去重）
     * - 尊重 onMentioned 偏好开关
     */
    @Async("notificationExecutor")
    public void notifyMentioned(Issue issue, String commentContent, Long commenterId) {
        try {
            Set<String> mentionedUsernames = extractMentions(commentContent);
            if (mentionedUsernames.isEmpty()) {
                return;
            }

            // 批量查询被提及的用户
            List<SysUser> mentionedUsers = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getUsername, mentionedUsernames)
            );
            if (mentionedUsers.isEmpty()) {
                return;
            }

            String commenterName = getUserDisplayName(commenterId);
            String title = String.format("%s 在评论中提到了你", commenterName);
            String content = String.format("%s 在工单 [%s] %s 的评论中提到了你",
                    commenterName, issue.getIssueKey(), issue.getTitle());

            int sent = 0;
            for (SysUser user : mentionedUsers) {
                // 排除评论者自己
                if (user.getId().equals(commenterId)) {
                    continue;
                }
                // 检查 onMentioned 偏好
                if (!preferenceService.isEnabled(user.getId(), NotificationEventType.MENTIONED, issue.getProjectId())) {
                    continue;
                }
                notificationService.notify(user.getId(), commenterId, title, content,
                        NotificationType.mention, "issue", issue.getId(), issue.getProjectId());
                sent++;
            }
            if (sent > 0) {
                log.debug("[IssueNotification] 已发送@提及通知: issue={}, mentionedUsers={}",
                        issue.getIssueKey(), sent);
            }
        } catch (Exception e) {
            log.error("[IssueNotification] 发送@提及通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    /**
     * 从 HTML 内容中提取 @username 列表。
     * 先去除 HTML 标签得到纯文本，再用正则匹配。
     */
    private Set<String> extractMentions(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return Collections.emptySet();
        }
        // 去除 HTML 标签，保留纯文本
        String plainText = htmlContent.replaceAll("<[^>]+>", " ");
        Set<String> usernames = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(plainText);
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        return usernames;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 收集评论通知接收人：报告人 + 负责人 + 之前评论者（去重，排除评论者自己）
     * 使用 SELECT DISTINCT user_id 优化，避免加载全量评论对象。
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
        // 之前的评论者（SELECT DISTINCT user_id，仅返回 Long 列表）
        List<Long> commenterIds = commentMapper.selectDistinctCommenterIds(issue.getId());
        if (commenterIds != null) {
            recipients.addAll(commenterIds);
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
     * 判断指定状态是否属于"已关闭"类别。
     * 用于区分 ISSUE_RESOLVED 和 ISSUE_STATUS_CHANGED 偏好检查。
     */
    private boolean isClosedStatus(Long statusId) {
        if (statusId == null) {
            return false;
        }
        try {
            IssueStatus status = statusMapper.selectById(statusId);
            return status != null && Boolean.TRUE.equals(status.getIsClosed());
        } catch (Exception e) {
            log.warn("[IssueNotification] 查询状态 isClosed 失败: statusId={}", statusId);
            return false;
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
     * 获取状态的中文显示名称（优先 displayName，fallback 到 name）
     */
    private String getStatusName(Long statusId) {
        if (statusId == null) {
            return "未知";
        }
        try {
            IssueStatus status = statusMapper.selectById(statusId);
            return status != null ? status.getLocalizedName() : String.valueOf(statusId);
        } catch (Exception e) {
            return String.valueOf(statusId);
        }
    }
}

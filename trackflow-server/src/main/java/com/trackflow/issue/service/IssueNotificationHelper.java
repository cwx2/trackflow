package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.notification.AbstractNotificationHelper;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationOutboxWriter;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
public class IssueNotificationHelper extends AbstractNotificationHelper {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationOutboxWriter outboxWriter;
    private final IssueCommentMapper commentMapper;
    private final IssueStatusMapper statusMapper;
    private final ProjectMapper projectMapper;

    public IssueNotificationHelper(NotificationService notificationService,
                                   NotificationPreferenceService preferenceService,
                                   NotificationOutboxWriter outboxWriter,
                                   IssueCommentMapper commentMapper,
                                   IssueStatusMapper statusMapper,
                                   ProjectMapper projectMapper,
                                   SysUserMapper sysUserMapper) {
        super(sysUserMapper, null); // IssueNotificationHelper 不需要 ProjectMemberMapper
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.outboxWriter = outboxWriter;
        this.commentMapper = commentMapper;
        this.statusMapper = statusMapper;
        this.projectMapper = projectMapper;
    }

    // ==================== 公共通知方法 ====================

    /**
     * 工单被分配时通知被分配人
     */
    @Async("notificationExecutor")
    public void notifyAssigned(Issue issue, Long assigneeId, Long operatorId) {
        if (assigneeId == null) {
            return;
        }
        // 如果被分配人就是操作者自己，检查 notifyOwnChanges 偏好
        if (assigneeId.equals(operatorId)) {
            if (!preferenceService.isNotifyOwnChanges(operatorId, issue.getProjectId())) {
                return;
            }
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
                    NotificationType.issue_assigned, NotificationReason.assigned,
                    "issue", issue.getId(), issue.getProjectId());
            log.debug("[IssueNotification] 已发送分配通知: issue={}, assignee={}", issue.getIssueKey(), assigneeId);
        } catch (Exception e) {
            log.error("[IssueNotification] 发送分配通知失败: issue={}, assignee={}, error={}",
                    issue.getIssueKey(), assigneeId, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyAssigned", e, outboxWriter.buildNotifyParams(
                    assigneeId, operatorId,
                    String.format("你被分配了工单 %s", issue.getIssueKey()),
                    String.format("工单 [%s] %s 被分配给了你", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_assigned.name(), NotificationReason.assigned.name(),
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 新评论通知：通知报告人 + 负责人 + 之前评论者（去重，排除当前用户）。
     * 每个接收者根据其角色获得对应的 reason。
     * <p>
     * 性能优化：使用批量偏好查询 + 按 reason 分组批量通知，
     * 将 DB 操作从 N×3 次降至常数级（最多 3 组 × 2-3 次）。
     */
    @Async("notificationExecutor")
    public void notifyCommented(Issue issue, Long commenterId) {
        try {
            // 根据评论者的 notifyOwnChanges 偏好决定是否排除自己
            boolean excludeSelf = !preferenceService.isNotifyOwnChanges(commenterId, issue.getProjectId());
            Long excludeUserId = excludeSelf ? commenterId : null;
            Map<Long, NotificationReason> recipientReasons = collectCommentRecipientsWithReason(issue, excludeUserId);
            if (recipientReasons.isEmpty()) {
                return;
            }

            // 批量偏好过滤（1 次 SELECT 替代 N 次 isEnabled 调用）
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipientReasons.keySet(), NotificationEventType.ISSUE_COMMENTED, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String commenterName = getUserDisplayName(commenterId);
            String title = String.format("%s 有新评论", issue.getIssueKey());
            String content = String.format("%s 在工单 [%s] %s 中添加了评论",
                    commenterName, issue.getIssueKey(), issue.getTitle());

            // 按 reason 分组后批量调用（最多 3 组：assigned/reporter/commenter）
            batchNotifyByReason(enabledUserIds, recipientReasons, commenterId, title, content,
                    NotificationType.issue_commented, "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送评论通知: issue={}, recipients={}",
                    issue.getIssueKey(), enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送评论通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
            // 评论通知涉及多用户，无法精确重构参数，保存概要信息供管理员审查
            outboxWriter.saveForRetry("notifyCommented", e, outboxWriter.buildNotifyParams(
                    null, commenterId,
                    String.format("%s 有新评论", issue.getIssueKey()),
                    String.format("工单 [%s] %s 中有新评论", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_commented.name(), null,
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 状态变更通知：通知报告人 + 负责人（去重，根据偏好决定是否排除操作者）。
     * 每个接收者根据其角色获得对应的 reason。
     * <p>
     * 当新状态属于"已关闭"类别（isClosed=true）时，使用 ISSUE_RESOLVED 偏好检查；
     * 否则使用 ISSUE_STATUS_CHANGED 偏好检查。
     * <p>
     * 性能优化：使用批量偏好查询 + 按 reason 分组批量通知，
     * 将 DB 操作从 N×3 次降至常数级。
     */
    @Async("notificationExecutor")
    public void notifyStatusChanged(Issue issue, Long oldStatusId, Long newStatusId, Long operatorId) {
        try {
            // 根据操作者的 notifyOwnChanges 偏好决定是否排除自己
            boolean excludeSelf = !preferenceService.isNotifyOwnChanges(operatorId, issue.getProjectId());
            Long excludeUserId = excludeSelf ? operatorId : null;
            Map<Long, NotificationReason> recipientReasons = collectStatusChangeRecipientsWithReason(issue, excludeUserId);
            if (recipientReasons.isEmpty()) {
                return;
            }

            // 循环外一次性获取所有共享数据
            String operatorName = getUserDisplayName(operatorId);
            String oldStatusName = getStatusName(oldStatusId);

            // 一次查询 newStatus 对象，复用获取名称和 isClosed 判断（消除重复 selectById）
            IssueStatus newStatus = statusMapper.selectById(newStatusId);
            String newStatusName = newStatus != null ? newStatus.getLocalizedName() : String.valueOf(newStatusId);
            boolean isClosed = newStatus != null && Boolean.TRUE.equals(newStatus.getIsClosed());

            String title = String.format("%s 状态变更为 %s", issue.getIssueKey(), newStatusName);
            String content = String.format("%s 将工单 [%s] %s 的状态从「%s」变更为「%s」",
                    operatorName, issue.getIssueKey(), issue.getTitle(), oldStatusName, newStatusName);

            // 判断新状态是否为"已关闭"类别——若是则使用 ISSUE_RESOLVED 偏好
            NotificationEventType eventType = isClosed
                    ? NotificationEventType.ISSUE_RESOLVED
                    : NotificationEventType.ISSUE_STATUS_CHANGED;

            // 批量偏好过滤（1 次 SELECT 替代 N 次 isEnabled 调用）
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipientReasons.keySet(), eventType, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            // 按 reason 分组后批量调用（最多 2 组：assigned/reporter）
            batchNotifyByReason(enabledUserIds, recipientReasons, operatorId, title, content,
                    NotificationType.issue_status_changed, "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送状态变更通知: issue={}, eventType={}, recipients={}",
                    issue.getIssueKey(), eventType, enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送状态变更通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifyStatusChanged", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("%s 状态变更", issue.getIssueKey()),
                    String.format("工单 [%s] %s 状态已变更", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_status_changed.name(), null,
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 工单创建时通知被分配人（若指定了 assignee）
     */
    @Async("notificationExecutor")
    public void notifyCreated(Issue issue, Long creatorId) {
        if (issue.getAssigneeId() == null) {
            return;
        }
        // 如果被分配人就是创建者自己，检查 notifyOwnChanges 偏好
        if (issue.getAssigneeId().equals(creatorId)) {
            if (!preferenceService.isNotifyOwnChanges(creatorId, issue.getProjectId())) {
                return;
            }
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
                    NotificationType.issue_assigned, NotificationReason.assigned,
                    "issue", issue.getId(), issue.getProjectId());
            log.debug("[IssueNotification] 已发送创建通知: issue={}, assignee={}",
                    issue.getIssueKey(), issue.getAssigneeId());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送创建通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifyCreated", e, outboxWriter.buildNotifyParams(
                    issue.getAssigneeId(), creatorId,
                    String.format("你被分配了新工单 %s", issue.getIssueKey()),
                    String.format("工单 [%s] %s 被创建并分配给了你", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_assigned.name(), NotificationReason.assigned.name(),
                    "issue", issue.getId(), issue.getProjectId()));
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
     * - 排除评论者自己（不通知自己提及自己），除非启用了 notifyOwnChanges
     * - 尊重 onMentioned 偏好开关
     * <p>
     * 性能优化：使用批量偏好查询 + notifyBatch，将 N 次 DB 操作降至常数级。
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

            // 收集候选接收者 ID（排除评论者自己，除非启用 notifyOwnChanges）
            Set<Long> candidateIds = new HashSet<>();
            for (SysUser user : mentionedUsers) {
                if (user.getId().equals(commenterId)) {
                    if (!preferenceService.isNotifyOwnChanges(commenterId, issue.getProjectId())) {
                        continue;
                    }
                }
                candidateIds.add(user.getId());
            }
            if (candidateIds.isEmpty()) {
                return;
            }

            // 批量偏好过滤（1 次 SELECT 替代 N 次 isEnabled 调用）
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    candidateIds, NotificationEventType.MENTIONED, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String commenterName = getUserDisplayName(commenterId);
            String title = String.format("%s 在评论中提到了你", commenterName);
            String content = String.format("%s 在工单 [%s] %s 的评论中提到了你",
                    commenterName, issue.getIssueKey(), issue.getTitle());

            // 所有 @mention 通知 reason 相同，直接批量调用
            notificationService.notifyBatch(enabledUserIds, commenterId, title, content,
                    NotificationType.mention, NotificationReason.mentioned,
                    "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送@提及通知: issue={}, mentionedUsers={}",
                    issue.getIssueKey(), enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送@提及通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifyMentioned", e, outboxWriter.buildNotifyParams(
                    null, commenterId,
                    String.format("评论中被提及: %s", issue.getIssueKey()),
                    String.format("工单 [%s] %s 的评论中有 @提及", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.mention.name(), NotificationReason.mentioned.name(),
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 工单移动到其他项目通知。
     * 通知报告人和负责人（如果有）。
     * <p>
     * 性能优化：使用批量偏好查询 + 按 reason 分组批量通知。
     */
    public void notifyMoved(Issue issue, Long sourceProjectId, Long targetProjectId, Long operatorId) {
        try {
            boolean excludeSelf = !preferenceService.isNotifyOwnChanges(operatorId, issue.getProjectId());
            Long excludeUserId = excludeSelf ? operatorId : null;

            Map<Long, NotificationReason> recipientReasons = collectStatusChangeRecipientsWithReason(issue, excludeUserId);
            if (recipientReasons.isEmpty()) {
                return;
            }

            // 批量偏好过滤（使用 ISSUE_STATUS_CHANGED 偏好，移动属于重大变更类通知）
            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipientReasons.keySet(), NotificationEventType.ISSUE_STATUS_CHANGED, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String operatorName = getUserDisplayName(operatorId);
            String sourceProjectName = getProjectName(sourceProjectId);
            String targetProjectName = getProjectName(targetProjectId);

            String title = String.format("%s 已移动到项目 %s", issue.getIssueKey(), targetProjectName);
            String content = String.format("%s 将工单 [%s] %s 从项目「%s」移动到项目「%s」",
                    operatorName, issue.getIssueKey(), issue.getTitle(), sourceProjectName, targetProjectName);

            // 按 reason 分组后批量调用
            batchNotifyByReason(enabledUserIds, recipientReasons, operatorId, title, content,
                    NotificationType.issue_moved, "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送移动通知: issue={}, from={}, to={}, recipients={}",
                    issue.getIssueKey(), sourceProjectName, targetProjectName, enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送移动通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifyMoved", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("%s 已移动到其他项目", issue.getIssueKey()),
                    String.format("工单 [%s] %s 已被移动", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_moved.name(), null,
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 工单取消/废弃通知：通知报告人和负责人。
     * <p>
     * 使用 ISSUE_STATUS_CHANGED 偏好（取消属于状态变更的特殊类型）。
     */
    @Async("notificationExecutor")
    public void notifyCancelled(Issue issue, Long operatorId) {
        try {
            boolean excludeSelf = !preferenceService.isNotifyOwnChanges(operatorId, issue.getProjectId());
            Long excludeUserId = excludeSelf ? operatorId : null;
            Map<Long, NotificationReason> recipientReasons = collectStatusChangeRecipientsWithReason(issue, excludeUserId);
            if (recipientReasons.isEmpty()) {
                return;
            }

            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipientReasons.keySet(), NotificationEventType.ISSUE_STATUS_CHANGED, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String operatorName = getUserDisplayName(operatorId);
            String title = String.format("%s 已被取消", issue.getIssueKey());
            String content = String.format("%s 取消了工单 [%s] %s",
                    operatorName, issue.getIssueKey(), issue.getTitle());

            batchNotifyByReason(enabledUserIds, recipientReasons, operatorId, title, content,
                    NotificationType.issue_status_changed, "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送取消通知: issue={}, recipients={}",
                    issue.getIssueKey(), enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送取消通知失败: issue={}, error={}",
                    issue.getIssueKey(), e.getMessage(), e);
            outboxWriter.saveForRetry("notifyCancelled", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("%s 已被取消", issue.getIssueKey()),
                    String.format("工单 [%s] %s 已被取消", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_status_changed.name(), null,
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 通用字段变更通知：通知报告人和负责人。
     * <p>
     * 覆盖 priority、dueDate、description、sprint、parent、tags 等字段变更。
     * 使用 ISSUE_UPDATED 偏好检查——用户可独立控制是否接收此类通知。
     */
    @Async("notificationExecutor")
    public void notifyFieldUpdated(Issue issue, String fieldName, String oldValue, String newValue, Long operatorId) {
        try {
            boolean excludeSelf = !preferenceService.isNotifyOwnChanges(operatorId, issue.getProjectId());
            Long excludeUserId = excludeSelf ? operatorId : null;
            Map<Long, NotificationReason> recipientReasons = collectStatusChangeRecipientsWithReason(issue, excludeUserId);
            if (recipientReasons.isEmpty()) {
                return;
            }

            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipientReasons.keySet(), NotificationEventType.ISSUE_UPDATED, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String operatorName = getUserDisplayName(operatorId);
            String fieldLabel = getFieldLabel(fieldName);
            String title = String.format("%s %s已更新", issue.getIssueKey(), fieldLabel);

            String content;
            if (oldValue != null && newValue != null) {
                content = String.format("%s 将工单 [%s] %s 的%s从「%s」变更为「%s」",
                        operatorName, issue.getIssueKey(), issue.getTitle(), fieldLabel, oldValue, newValue);
            } else if (newValue != null) {
                content = String.format("%s 设置了工单 [%s] %s 的%s为「%s」",
                        operatorName, issue.getIssueKey(), issue.getTitle(), fieldLabel, newValue);
            } else if (oldValue != null) {
                content = String.format("%s 清除了工单 [%s] %s 的%s（原值「%s」）",
                        operatorName, issue.getIssueKey(), issue.getTitle(), fieldLabel, oldValue);
            } else {
                content = String.format("%s 更新了工单 [%s] %s 的%s",
                        operatorName, issue.getIssueKey(), issue.getTitle(), fieldLabel);
            }

            batchNotifyByReason(enabledUserIds, recipientReasons, operatorId, title, content,
                    NotificationType.issue_updated, "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送字段变更通知: issue={}, field={}, recipients={}",
                    issue.getIssueKey(), fieldName, enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送字段变更通知失败: issue={}, field={}, error={}",
                    issue.getIssueKey(), fieldName, e.getMessage(), e);
            outboxWriter.saveForRetry("notifyFieldUpdated", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("%s %s已更新", issue.getIssueKey(), getFieldLabel(fieldName)),
                    String.format("工单 [%s] %s 的%s已变更", issue.getIssueKey(), issue.getTitle(), getFieldLabel(fieldName)),
                    NotificationType.issue_updated.name(), null,
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 发送多字段同时变更的合并通知。
     * <p>
     * 当用户在一次 API 调用中同时修改多个字段时，将所有变更合并为一条通知，
     * 避免聚合机制覆盖式更新导致前面字段的变更信息丢失。
     * <p>
     * 如果仅变更了一个字段，退化为与 {@link #notifyFieldUpdated} 相同的行为。
     *
     * @param issue      变更后的 Issue 实体
     * @param changes    变更字段映射：fieldName → [oldValue, newValue]
     * @param operatorId 操作者 ID
     */
    @Async("notificationExecutor")
    public void notifyMultiFieldUpdated(Issue issue, Map<String, String[]> changes, Long operatorId) {
        try {
            if (changes == null || changes.isEmpty()) {
                return;
            }

            // 如果只有一个字段变更，退化为单字段通知逻辑
            if (changes.size() == 1) {
                Map.Entry<String, String[]> entry = changes.entrySet().iterator().next();
                String[] vals = entry.getValue();
                notifyFieldUpdated(issue, entry.getKey(), vals[0], vals[1], operatorId);
                return;
            }

            boolean excludeSelf = !preferenceService.isNotifyOwnChanges(operatorId, issue.getProjectId());
            Long excludeUserId = excludeSelf ? operatorId : null;
            Map<Long, NotificationReason> recipientReasons = collectStatusChangeRecipientsWithReason(issue, excludeUserId);
            if (recipientReasons.isEmpty()) {
                return;
            }

            Set<Long> enabledUserIds = preferenceService.getEnabledUserIds(
                    recipientReasons.keySet(), NotificationEventType.ISSUE_UPDATED, issue.getProjectId());
            if (enabledUserIds.isEmpty()) {
                return;
            }

            String operatorName = getUserDisplayName(operatorId);
            String title = String.format("%s 有 %d 个字段更新", issue.getIssueKey(), changes.size());

            // 组装多字段变更详情
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s 更新了工单 [%s] %s：\n", operatorName, issue.getIssueKey(), issue.getTitle()));
            for (Map.Entry<String, String[]> entry : changes.entrySet()) {
                String fieldLabel = getFieldLabel(entry.getKey());
                String[] vals = entry.getValue();
                String oldVal = vals[0];
                String newVal = vals[1];
                if (oldVal != null && newVal != null) {
                    sb.append(String.format("• %s：%s → %s\n", fieldLabel, oldVal, newVal));
                } else if (newVal != null) {
                    sb.append(String.format("• %s：设置为「%s」\n", fieldLabel, newVal));
                } else if (oldVal != null) {
                    sb.append(String.format("• %s：已清除（原值「%s」）\n", fieldLabel, oldVal));
                } else {
                    sb.append(String.format("• %s：已更新\n", fieldLabel));
                }
            }
            String content = sb.toString().stripTrailing();

            batchNotifyByReason(enabledUserIds, recipientReasons, operatorId, title, content,
                    NotificationType.issue_updated, "issue", issue.getId(), issue.getProjectId());

            log.debug("[IssueNotification] 已发送多字段变更通知: issue={}, fields={}, recipients={}",
                    issue.getIssueKey(), changes.keySet(), enabledUserIds.size());
        } catch (Exception e) {
            log.error("[IssueNotification] 发送多字段变更通知失败: issue={}, fields={}, error={}",
                    issue.getIssueKey(), changes != null ? changes.keySet() : "null", e.getMessage(), e);
            outboxWriter.saveForRetry("notifyMultiFieldUpdated", e, outboxWriter.buildNotifyParams(
                    null, operatorId,
                    String.format("%s 有多个字段更新", issue.getIssueKey()),
                    String.format("工单 [%s] %s 的多个字段已变更", issue.getIssueKey(), issue.getTitle()),
                    NotificationType.issue_updated.name(), null,
                    "issue", issue.getId(), issue.getProjectId()));
        }
    }

    /**
     * 获取字段的中文显示标签。
     */
    private String getFieldLabel(String fieldName) {
        return switch (fieldName) {
            case "priority" -> "优先级";
            case "due_date" -> "截止日期";
            case "description" -> "描述";
            case "sprint" -> "迭代";
            case "parent" -> "父工单";
            case "tags" -> "标签";
            case "title" -> "标题";
            case "issue_type" -> "类型";
            case "estimated_hours" -> "预估工时";
            default -> fieldName;
        };
    }

    private String getProjectName(Long projectId) {
        if (projectId == null) return "未知";
        try {
            var project = projectMapper.selectById(projectId);
            return project != null ? project.getName() : String.valueOf(projectId);
        } catch (Exception e) {
            return String.valueOf(projectId);
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
     * 按 reason 分组后批量调用 notifyBatch。
     * <p>
     * 将已通过偏好过滤的 enabledUserIds 按其对应的 reason 分组，
     * 每组调用一次 notifyBatch（最多 3 组：assigned/reporter/commenter），
     * 确保每个接收者的通知 reason 正确记录。
     *
     * @param enabledUserIds   通过偏好过滤后的接收者集合
     * @param recipientReasons 接收者 → reason 映射（原始完整集合，含未通过偏好过滤的）
     * @param actorId          操作者 ID
     * @param title            通知标题
     * @param content          通知内容
     * @param type             通知类型
     * @param resourceType     关联资源类型
     * @param resourceId       关联资源 ID
     * @param projectId        项目 ID
     */
    private void batchNotifyByReason(Set<Long> enabledUserIds, Map<Long, NotificationReason> recipientReasons,
                                     Long actorId, String title, String content,
                                     NotificationType type, String resourceType, Long resourceId, Long projectId) {
        // 按 reason 分组（只保留通过偏好过滤的用户）
        Map<NotificationReason, Set<Long>> grouped = enabledUserIds.stream()
                .collect(Collectors.groupingBy(
                        id -> recipientReasons.get(id),
                        Collectors.toSet()));

        // 每组一次批量调用（最多 3 组：assigned/reporter/commenter）
        for (Map.Entry<NotificationReason, Set<Long>> entry : grouped.entrySet()) {
            notificationService.notifyBatch(entry.getValue(), actorId, title, content,
                    type, entry.getKey(), resourceType, resourceId, projectId);
        }
    }

    /**
     * 收集评论通知接收人及其 reason：
     * - 报告人 → reporter
     * - 负责人 → assigned
     * - 之前评论者 → commenter
     * 去重，排除评论者自己。若一人有多个角色，优先级：assigned > reporter > commenter。
     */
    private Map<Long, NotificationReason> collectCommentRecipientsWithReason(Issue issue, Long excludeUserId) {
        Map<Long, NotificationReason> recipients = new LinkedHashMap<>();

        // 之前的评论者优先级最低，先加入（后续会被更高优先级覆盖）
        List<Long> commenterIds = commentMapper.selectDistinctCommenterIds(issue.getId());
        if (commenterIds != null) {
            for (Long id : commenterIds) {
                recipients.put(id, NotificationReason.commenter);
            }
        }

        // 报告人优先级中等
        if (issue.getReporterId() != null) {
            recipients.put(issue.getReporterId(), NotificationReason.reporter);
        }

        // 负责人优先级最高
        if (issue.getAssigneeId() != null) {
            recipients.put(issue.getAssigneeId(), NotificationReason.assigned);
        }

        // 排除当前操作者
        recipients.remove(excludeUserId);
        return recipients;
    }

    /**
     * 收集状态变更通知接收人及其 reason：
     * - 报告人 → reporter
     * - 负责人 → assigned
     * 去重，排除操作者自己。
     */
    private Map<Long, NotificationReason> collectStatusChangeRecipientsWithReason(Issue issue, Long excludeUserId) {
        Map<Long, NotificationReason> recipients = new LinkedHashMap<>();

        if (issue.getReporterId() != null) {
            recipients.put(issue.getReporterId(), NotificationReason.reporter);
        }
        if (issue.getAssigneeId() != null) {
            recipients.put(issue.getAssigneeId(), NotificationReason.assigned);
        }

        recipients.remove(excludeUserId);
        return recipients;
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

package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.service.DistributedLockService;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.mapper.NotificationPreferenceMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日期告警定时任务：每日扫描即将到期和已逾期的工单，向负责人发送通知。
 * <p>
 * 执行时间：每天 08:00
 * <p>
 * 通知规则：
 * - 即将到期：due_date 在 [TODAY, TODAY + advance_days] 范围内的未关闭工单
 * - 已逾期：due_date < TODAY 且状态未关闭的工单
 * - 仅通知工单负责人（assignee_id），无负责人时跳过
 * - 尊重用户偏好 on_due_date / on_overdue
 * - 去重：同一工单的同类日期告警每天最多发送一次（通过通知聚合窗口实现）
 * <p>
 * 参考 OpenProject: notifications/workflow_job.rb — date_alert 逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DateAlertScheduler {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final NotificationService notificationService;
    private final NotificationPreferenceMapper preferenceMapper;
    private final SysUserMapper sysUserMapper;
    private final SystemSettingService systemSettingService;
    private final DistributedLockService distributedLockService;

    /**
     * 每日 08:00 执行日期告警扫描
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scanDateAlerts() {
        distributedLockService.executeWithLock("date_alert_scan", this::doScanDateAlerts);
    }

    private void doScanDateAlerts() {
        // 全局开关检查
        if (!isDateAlertEnabled()) {
            log.debug("[DateAlert] 全局日期告警已关闭，跳过扫描");
            return;
        }

        log.info("[DateAlert] 开始每日日期告警扫描...");
        LocalDate today = LocalDate.now();

        try {
            // 1. 获取所有已关闭状态的 ID 集合（用于排除）
            Set<Long> closedStatusIds = getClosedStatusIds();

            // 2. 扫描即将到期的工单
            int dueDateCount = processDueDateApproaching(today, closedStatusIds);

            // 3. 扫描已逾期的工单
            int overdueCount = processOverdue(today, closedStatusIds);

            log.info("[DateAlert] 扫描完成：到期提醒 {} 条，逾期提醒 {} 条", dueDateCount, overdueCount);
        } catch (Exception e) {
            log.error("[DateAlert] 日期告警扫描过程中发生异常", e);
        }
    }

    /**
     * 处理即将到期的工单。
     * <p>
     * 策略：按用户偏好中的 advance_days 查找匹配的工单。
     * 由于不同用户可能有不同的 advance_days 配置，这里使用最大可能范围（14天）
     * 查询一次工单，然后按每个负责人的偏好过滤。
     */
    private int processDueDateApproaching(LocalDate today, Set<Long> closedStatusIds) {
        // 查询 due_date 在 [today, today + 14天] 范围内的未关闭、未删除、有负责人的工单
        LocalDate maxEndDate = today.plusDays(14);

        List<Issue> approachingIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .isNull(Issue::getDeletedAt)
                        .isNotNull(Issue::getAssigneeId)
                        .isNotNull(Issue::getDueDate)
                        .ge(Issue::getDueDate, today)
                        .le(Issue::getDueDate, maxEndDate)
                        .notIn(!closedStatusIds.isEmpty(), Issue::getStatusId, closedStatusIds)
        );

        if (approachingIssues.isEmpty()) {
            return 0;
        }

        // 按负责人分组
        Map<Long, List<Issue>> byAssignee = approachingIssues.stream()
                .collect(Collectors.groupingBy(Issue::getAssigneeId));

        int notifiedCount = 0;
        for (Map.Entry<Long, List<Issue>> entry : byAssignee.entrySet()) {
            Long assigneeId = entry.getKey();
            List<Issue> issues = entry.getValue();

            // 获取该用户的偏好
            NotificationPreference pref = getGlobalPreference(assigneeId);
            if (!Boolean.TRUE.equals(pref.getOnDueDate()) && !isDefaultEnabled(pref.getOnDueDate())) {
                continue;
            }

            int advanceDays = pref.getDueDateAdvanceDays() != null ? pref.getDueDateAdvanceDays() : 1;

            // 过滤：只保留 due_date <= today + advance_days 的工单
            LocalDate cutoff = today.plusDays(advanceDays);
            for (Issue issue : issues) {
                if (!issue.getDueDate().isAfter(cutoff)) {
                    sendDueDateNotification(issue, assigneeId, today);
                    notifiedCount++;
                }
            }
        }
        return notifiedCount;
    }

    /**
     * 默认逾期提醒间隔（天数）。仅在逾期第 1、3、7、14 天发送通知。
     * 参考 OpenProject: DATE_ALERT_OVERDUE_DURATIONS = [nil, 1, 3, 7]
     */
    private static final int[] DEFAULT_OVERDUE_REMINDER_DAYS = {1, 3, 7, 14};

    /**
     * 处理已逾期的工单。
     * <p>
     * 策略（参考 OpenProject alertable_work_packages.rb）：
     * 1. 计算每个工单的逾期天数
     * 2. 按用户偏好的递增间隔过滤（默认第1天、第3天、第7天、第14天）
     * 3. 发送通知前将同一工单之前的未读逾期通知标记为已读（避免累积）
     */
    private int processOverdue(LocalDate today, Set<Long> closedStatusIds) {
        // 查询 due_date < today 的未关闭、未删除、有负责人的工单
        List<Issue> overdueIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .isNull(Issue::getDeletedAt)
                        .isNotNull(Issue::getAssigneeId)
                        .isNotNull(Issue::getDueDate)
                        .lt(Issue::getDueDate, today)
                        .notIn(!closedStatusIds.isEmpty(), Issue::getStatusId, closedStatusIds)
        );

        if (overdueIssues.isEmpty()) {
            return 0;
        }

        // 按负责人分组
        Map<Long, List<Issue>> byAssignee = overdueIssues.stream()
                .collect(Collectors.groupingBy(Issue::getAssigneeId));

        int notifiedCount = 0;
        for (Map.Entry<Long, List<Issue>> entry : byAssignee.entrySet()) {
            Long assigneeId = entry.getKey();
            List<Issue> issues = entry.getValue();

            // 检查用户偏好
            NotificationPreference pref = getGlobalPreference(assigneeId);
            if (!Boolean.TRUE.equals(pref.getOnOverdue()) && !isDefaultEnabled(pref.getOnOverdue())) {
                continue;
            }

            // 获取用户配置的逾期提醒间隔（默认 [1,3,7,14]）
            Set<Integer> reminderDays = getOverdueReminderDaysSet(pref);

            for (Issue issue : issues) {
                long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(issue.getDueDate(), today);
                if (shouldSendOverdueReminder(overdueDays, reminderDays)) {
                    // 先标记该工单之前的逾期通知为已读（避免累积，参考 OpenProject mark_previous_notifications_as_read）
                    notificationService.markPreviousOverdueAsRead(assigneeId, issue.getId());
                    sendOverdueNotification(issue, assigneeId, today);
                    notifiedCount++;
                }
            }
        }
        return notifiedCount;
    }

    /**
     * 判断是否应在指定逾期天数发送提醒。
     * <p>
     * 逻辑：精确匹配前几个配置天数；超出最大配置天数后，按最大间隔周期重复。
     * 例如配置 [1,3,7,14]：第1天、第3天、第7天、第14天发送，
     * 之后每隔 14 天发送一次（第28天、第42天……），避免完全静默。
     */
    private boolean shouldSendOverdueReminder(long overdueDays, Set<Integer> reminderDays) {
        if (overdueDays <= 0) {
            return false;
        }
        // 精确匹配配置的天数
        if (reminderDays.contains((int) overdueDays)) {
            return true;
        }
        // 超出最大天数后，按最大间隔周期重复
        int maxDay = reminderDays.stream().mapToInt(Integer::intValue).max().orElse(14);
        if (overdueDays > maxDay && maxDay > 0) {
            return (overdueDays - maxDay) % maxDay == 0;
        }
        return false;
    }

    /**
     * 从用户偏好获取逾期提醒天数集合
     */
    private Set<Integer> getOverdueReminderDaysSet(NotificationPreference pref) {
        Integer[] days = pref.getOverdueReminderDays();
        if (days == null || days.length == 0) {
            return Arrays.stream(DEFAULT_OVERDUE_REMINDER_DAYS).boxed().collect(Collectors.toSet());
        }
        Set<Integer> result = new HashSet<>();
        for (Integer day : days) {
            if (day != null && day > 0) {
                result.add(day);
            }
        }
        return result.isEmpty()
                ? Arrays.stream(DEFAULT_OVERDUE_REMINDER_DAYS).boxed().collect(Collectors.toSet())
                : result;
    }

    /**
     * 发送到期提醒通知
     */
    private void sendDueDateNotification(Issue issue, Long assigneeId, LocalDate today) {
        try {
            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, issue.getDueDate());
            String dueDesc;
            if (daysUntilDue == 0) {
                dueDesc = "今日到期";
            } else if (daysUntilDue == 1) {
                dueDesc = "明日到期";
            } else {
                dueDesc = daysUntilDue + "天后到期";
            }

            String title = String.format("工单 %s %s", issue.getIssueKey(), dueDesc);
            String content = String.format("工单 [%s] %s 将于 %s 到期（%s）",
                    issue.getIssueKey(), issue.getTitle(), issue.getDueDate(), dueDesc);

            notificationService.notify(assigneeId, null, title, content,
                    NotificationType.due_date_alert, NotificationReason.assigned,
                    "issue", issue.getId(), issue.getProjectId());

            log.debug("[DateAlert] 发送到期提醒: issue={}, assignee={}, dueDate={}",
                    issue.getIssueKey(), assigneeId, issue.getDueDate());
        } catch (Exception e) {
            log.error("[DateAlert] 发送到期提醒失败: issue={}, assignee={}, error={}",
                    issue.getIssueKey(), assigneeId, e.getMessage());
        }
    }

    /**
     * 发送逾期通知
     */
    private void sendOverdueNotification(Issue issue, Long assigneeId, LocalDate today) {
        try {
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(issue.getDueDate(), today);
            String title = String.format("工单 %s 已逾期 %d 天", issue.getIssueKey(), overdueDays);
            String content = String.format("工单 [%s] %s 截止日期为 %s，已逾期 %d 天，请尽快处理",
                    issue.getIssueKey(), issue.getTitle(), issue.getDueDate(), overdueDays);

            notificationService.notify(assigneeId, null, title, content,
                    NotificationType.overdue_alert, NotificationReason.assigned,
                    "issue", issue.getId(), issue.getProjectId());

            log.debug("[DateAlert] 发送逾期提醒: issue={}, assignee={}, overdueDays={}",
                    issue.getIssueKey(), assigneeId, overdueDays);
        } catch (Exception e) {
            log.error("[DateAlert] 发送逾期提醒失败: issue={}, assignee={}, error={}",
                    issue.getIssueKey(), assigneeId, e.getMessage());
        }
    }

    /**
     * 获取所有已关闭状态的 ID
     */
    private Set<Long> getClosedStatusIds() {
        return issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>()
                        .eq(IssueStatus::getIsClosed, true)
                        .select(IssueStatus::getId)
        ).stream()
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户全局偏好（无记录时返回一个默认全 true 的对象）
     */
    private NotificationPreference getGlobalPreference(Long userId) {
        NotificationPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
                        .isNull(NotificationPreference::getProjectId)
        );
        if (pref == null) {
            // 无记录视为全部启用（默认行为）
            pref = new NotificationPreference();
            pref.setOnDueDate(true);
            pref.setOnOverdue(true);
            pref.setDueDateAdvanceDays(1);
        }
        return pref;
    }

    /**
     * NULL 视为默认启用
     */
    private boolean isDefaultEnabled(Boolean value) {
        return value == null;
    }

    /**
     * 检查全局日期告警开关
     */
    private boolean isDateAlertEnabled() {
        String value = systemSettingService.getSettingValue("notification.date_alert_enabled", "true");
        return Boolean.parseBoolean(value);
    }
}

package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.integration.dto.UpdateNotificationSettingsDTO;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.integration.vo.NotificationSettingsVO;
import com.trackflow.integration.vo.NotificationStatsVO;
import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知管理后台服务（管理员专用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final SystemSettingService systemSettingService;
    private final NotificationMapper notificationMapper;

    private static final String PREFIX = "notification.";

    /**
     * 获取全局通知设置
     */
    public NotificationSettingsVO getSettings() {
        NotificationSettingsVO vo = new NotificationSettingsVO();

        vo.setInAppEnabled(getBoolSetting("in_app_enabled", true));
        vo.setEmailEnabled(getBoolSetting("email_enabled", false));
        vo.setRetentionDays(getIntSetting("retention_days", 90));

        // 新用户默认偏好
        vo.setDefaultOnIssueAssigned(getBoolSetting("default_on_issue_assigned", true));
        vo.setDefaultOnIssueStatusChanged(getBoolSetting("default_on_issue_status_changed", true));
        vo.setDefaultOnIssueCommented(getBoolSetting("default_on_issue_commented", true));
        vo.setDefaultOnMentioned(getBoolSetting("default_on_mentioned", true));
        vo.setDefaultOnIssueResolved(getBoolSetting("default_on_issue_resolved", true));
        vo.setDefaultOnSprintStarted(getBoolSetting("default_on_sprint_started", false));
        vo.setDefaultOnSprintCompleted(getBoolSetting("default_on_sprint_completed", false));
        vo.setDefaultOnProjectMemberChanged(getBoolSetting("default_on_project_member_changed", true));
        vo.setDefaultOnProjectLifecycle(getBoolSetting("default_on_project_lifecycle", true));
        vo.setDefaultEmailEnabled(getBoolSetting("default_email_enabled", false));

        return vo;
    }

    /**
     * 更新全局通知设置
     */
    @Transactional
    public NotificationSettingsVO updateSettings(UpdateNotificationSettingsDTO dto) {
        if (dto.getInAppEnabled() != null) {
            upsertSetting("in_app_enabled", String.valueOf(dto.getInAppEnabled()),
                    "站内通知全局开关");
        }
        if (dto.getEmailEnabled() != null) {
            upsertSetting("email_enabled", String.valueOf(dto.getEmailEnabled()),
                    "邮件通知全局开关（需配置 SMTP）");
        }
        if (dto.getRetentionDays() != null) {
            if (dto.getRetentionDays() < 0) {
                throw new IllegalArgumentException("保留天数不能为负数");
            }
            upsertSetting("retention_days", String.valueOf(dto.getRetentionDays()),
                    "已读通知保留天数（0=永久保留）");
        }

        // 默认偏好
        if (dto.getDefaultOnIssueAssigned() != null) {
            upsertSetting("default_on_issue_assigned", String.valueOf(dto.getDefaultOnIssueAssigned()),
                    "新用户默认偏好：工单被分配时通知");
        }
        if (dto.getDefaultOnIssueStatusChanged() != null) {
            upsertSetting("default_on_issue_status_changed", String.valueOf(dto.getDefaultOnIssueStatusChanged()),
                    "新用户默认偏好：工单状态变更时通知");
        }
        if (dto.getDefaultOnIssueCommented() != null) {
            upsertSetting("default_on_issue_commented", String.valueOf(dto.getDefaultOnIssueCommented()),
                    "新用户默认偏好：工单被评论时通知");
        }
        if (dto.getDefaultOnMentioned() != null) {
            upsertSetting("default_on_mentioned", String.valueOf(dto.getDefaultOnMentioned()),
                    "新用户默认偏好：被 @ 提及时通知");
        }
        if (dto.getDefaultOnIssueResolved() != null) {
            upsertSetting("default_on_issue_resolved", String.valueOf(dto.getDefaultOnIssueResolved()),
                    "新用户默认偏好：工单被解决时通知");
        }
        if (dto.getDefaultOnSprintStarted() != null) {
            upsertSetting("default_on_sprint_started", String.valueOf(dto.getDefaultOnSprintStarted()),
                    "新用户默认偏好：Sprint 开始时通知");
        }
        if (dto.getDefaultOnSprintCompleted() != null) {
            upsertSetting("default_on_sprint_completed", String.valueOf(dto.getDefaultOnSprintCompleted()),
                    "新用户默认偏好：Sprint 完成时通知");
        }
        if (dto.getDefaultOnProjectMemberChanged() != null) {
            upsertSetting("default_on_project_member_changed", String.valueOf(dto.getDefaultOnProjectMemberChanged()),
                    "新用户默认偏好：项目成员变更时通知");
        }
        if (dto.getDefaultOnProjectLifecycle() != null) {
            upsertSetting("default_on_project_lifecycle", String.valueOf(dto.getDefaultOnProjectLifecycle()),
                    "新用户默认偏好：项目生命周期变更时通知");
        }
        if (dto.getDefaultEmailEnabled() != null) {
            upsertSetting("default_email_enabled", String.valueOf(dto.getDefaultEmailEnabled()),
                    "新用户默认偏好：是否启用邮件通知");
        }

        log.info("[NotificationAdmin] 通知设置已更新");
        return getSettings();
    }

    /**
     * 获取通知统计概览
     */
    public NotificationStatsVO getStats() {
        NotificationStatsVO stats = new NotificationStatsVO();

        // 总数
        long total = notificationMapper.selectCount(new LambdaQueryWrapper<>());
        stats.setTotalCount(total);

        // 未读数
        long unread = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().eq(Notification::getIsRead, false));
        stats.setUnreadCount(unread);
        stats.setReadCount(total - unread);

        // 今日发送量
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long today = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().ge(Notification::getCreatedAt, todayStart));
        stats.setTodayCount(today);

        // 本周发送量（周一开始）
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay();
        long week = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().ge(Notification::getCreatedAt, weekStart));
        stats.setWeekCount(week);

        // 类型分布（使用高效的 SQL GROUP BY）
        Map<String, Long> typeDistribution = new LinkedHashMap<>();
        List<Map<String, Object>> distRows = notificationMapper.selectTypeDistribution();
        for (Map<String, Object> row : distRows) {
            String type = (String) row.get("type");
            Long count = ((Number) row.get("cnt")).longValue();
            typeDistribution.put(type, count);
        }
        stats.setTypeDistribution(typeDistribution);

        return stats;
    }

    // === Private helpers ===

    private Boolean getBoolSetting(String shortKey, boolean defaultValue) {
        String value = systemSettingService.getSettingValue(PREFIX + shortKey, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    private Integer getIntSetting(String shortKey, int defaultValue) {
        String value = systemSettingService.getSettingValue(PREFIX + shortKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void upsertSetting(String shortKey, String value, String description) {
        systemSettingService.upsertSetting(PREFIX + shortKey, value, description, "notification");
    }
}

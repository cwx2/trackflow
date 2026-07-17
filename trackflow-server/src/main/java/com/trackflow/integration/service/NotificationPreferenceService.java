package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.integration.dto.UpdateNotificationPreferenceDTO;
import com.trackflow.integration.entity.NotificationEventType;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.mapper.NotificationPreferenceMapper;
import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceMapper preferenceMapper;
    private final SystemSettingService systemSettingService;

    /**
     * 检查用户对指定事件类型的通知偏好是否启用。
     * <p>
     * 统一入口——所有 NotificationHelper 通过此方法检查偏好，
     * 取代各自维护的字符串 switch-case 方法。
     *
     * @param userId    用户 ID
     * @param eventType 通知事件类型（编译期类型安全）
     * @return true 表示用户允许接收此类通知
     */
    public boolean isEnabled(Long userId, NotificationEventType eventType) {
        try {
            NotificationPreference pref = getByUserId(userId);
            return eventType.isEnabled(pref);
        } catch (Exception e) {
            log.warn("[NotificationPreference] 查询通知偏好失败: userId={}, eventType={}, 默认发送",
                    userId, eventType, e);
            return true; // 查询失败时默认发送，不阻断通知
        }
    }

    /**
     * 获取用户通知偏好，若不存在则创建默认记录
     */
    public NotificationPreference getByUserId(Long userId) {
        NotificationPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
        );
        if (pref == null) {
            pref = createDefault(userId);
        }
        return pref;
    }

    /**
     * 更新用户通知偏好
     */
    @Transactional
    public NotificationPreference update(Long userId, UpdateNotificationPreferenceDTO dto) {
        // 校验静音时段一致性：要么同时为 null，要么同时有值
        boolean hasStart = dto.getQuietHoursStart() != null && !dto.getQuietHoursStart().isEmpty();
        boolean hasEnd = dto.getQuietHoursEnd() != null && !dto.getQuietHoursEnd().isEmpty();
        if (hasStart != hasEnd) {
            throw new BusinessException(ErrorCode.QUIET_HOURS_INCOMPLETE);
        }

        NotificationPreference pref = getByUserId(userId);

        if (dto.getOnIssueAssigned() != null) {
            pref.setOnIssueAssigned(dto.getOnIssueAssigned());
        }
        if (dto.getOnIssueStatusChanged() != null) {
            pref.setOnIssueStatusChanged(dto.getOnIssueStatusChanged());
        }
        if (dto.getOnIssueCommented() != null) {
            pref.setOnIssueCommented(dto.getOnIssueCommented());
        }
        if (dto.getOnMentioned() != null) {
            pref.setOnMentioned(dto.getOnMentioned());
        }
        if (dto.getOnIssueResolved() != null) {
            pref.setOnIssueResolved(dto.getOnIssueResolved());
        }
        if (dto.getOnSprintStarted() != null) {
            pref.setOnSprintStarted(dto.getOnSprintStarted());
        }
        if (dto.getOnSprintCompleted() != null) {
            pref.setOnSprintCompleted(dto.getOnSprintCompleted());
        }
        if (dto.getOnProjectMemberChanged() != null) {
            pref.setOnProjectMemberChanged(dto.getOnProjectMemberChanged());
        }
        if (dto.getOnProjectLifecycle() != null) {
            pref.setOnProjectLifecycle(dto.getOnProjectLifecycle());
        }
        if (dto.getEmailEnabled() != null) {
            pref.setEmailEnabled(dto.getEmailEnabled());
        }
        // 静音时段允许设置为 null（清除）
        pref.setQuietHoursStart(dto.getQuietHoursStart());
        pref.setQuietHoursEnd(dto.getQuietHoursEnd());

        pref.setUpdatedAt(LocalDateTime.now());
        preferenceMapper.updateById(pref);
        return pref;
    }

    /**
     * 创建默认偏好记录（使用全局通知管理中配置的默认值）
     */
    private NotificationPreference createDefault(Long userId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        pref.setOnIssueAssigned(getDefaultBool("notification.default_on_issue_assigned", true));
        pref.setOnIssueStatusChanged(getDefaultBool("notification.default_on_issue_status_changed", true));
        pref.setOnIssueCommented(getDefaultBool("notification.default_on_issue_commented", true));
        pref.setOnMentioned(getDefaultBool("notification.default_on_mentioned", true));
        pref.setOnIssueResolved(getDefaultBool("notification.default_on_issue_resolved", true));
        pref.setOnSprintStarted(getDefaultBool("notification.default_on_sprint_started", false));
        pref.setOnSprintCompleted(getDefaultBool("notification.default_on_sprint_completed", false));
        pref.setOnProjectMemberChanged(getDefaultBool("notification.default_on_project_member_changed", true));
        pref.setOnProjectLifecycle(getDefaultBool("notification.default_on_project_lifecycle", true));
        pref.setEmailEnabled(getDefaultBool("notification.default_email_enabled", false));
        pref.setCreatedAt(LocalDateTime.now());
        pref.setUpdatedAt(LocalDateTime.now());
        preferenceMapper.insert(pref);
        return pref;
    }

    private boolean getDefaultBool(String key, boolean fallback) {
        String value = systemSettingService.getSettingValue(key, String.valueOf(fallback));
        return Boolean.parseBoolean(value);
    }
}

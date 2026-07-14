package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.integration.dto.UpdateNotificationPreferenceDTO;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.mapper.NotificationPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceMapper preferenceMapper;

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
            throw new BusinessException(40001, 400, "静音时段的开始和结束时间必须同时设置或同时清除");
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
     * 创建默认偏好记录
     */
    private NotificationPreference createDefault(Long userId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        pref.setOnIssueAssigned(true);
        pref.setOnIssueStatusChanged(true);
        pref.setOnIssueCommented(true);
        pref.setOnMentioned(true);
        pref.setOnIssueResolved(true);
        pref.setOnSprintStarted(false);
        pref.setOnSprintCompleted(false);
        pref.setEmailEnabled(false);
        pref.setCreatedAt(LocalDateTime.now());
        pref.setUpdatedAt(LocalDateTime.now());
        preferenceMapper.insert(pref);
        return pref;
    }
}

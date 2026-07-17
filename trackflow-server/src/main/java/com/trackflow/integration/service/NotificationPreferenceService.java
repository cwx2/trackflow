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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceMapper preferenceMapper;
    private final SystemSettingService systemSettingService;

    /**
     * 检查用户对指定事件类型的通知偏好是否启用（全局，向后兼容）。
     */
    public boolean isEnabled(Long userId, NotificationEventType eventType) {
        return isEnabled(userId, eventType, null);
    }

    /**
     * 检查用户对指定事件类型的通知偏好是否启用。
     * <p>
     * 实现"项目级覆盖全局"逻辑（参考 OpenProject applicable scope）：
     * 1. 若 projectId 非空且存在项目级偏好记录 → 使用项目级偏好
     * 2. 否则 → 使用全局偏好
     *
     * @param userId    用户 ID
     * @param eventType 通知事件类型
     * @param projectId 项目 ID（可选，NULL 表示使用全局偏好）
     * @return true 表示用户允许接收此类通知
     */
    public boolean isEnabled(Long userId, NotificationEventType eventType, Long projectId) {
        try {
            NotificationPreference pref = getApplicable(userId, projectId);
            return eventType.isEnabled(pref);
        } catch (Exception e) {
            log.warn("[NotificationPreference] 查询通知偏好失败: userId={}, eventType={}, projectId={}, 默认发送",
                    userId, eventType, projectId, e);
            return true; // 查询失败时默认发送，不阻断通知
        }
    }

    /**
     * 获取生效的偏好配置（项目级覆盖全局逻辑）。
     * <p>
     * 若 projectId 非空且存在该项目的偏好记录 → 返回项目级偏好
     * 否则 → 返回全局偏好（不存在则自动创建默认值）
     */
    public NotificationPreference getApplicable(Long userId, Long projectId) {
        if (projectId != null) {
            NotificationPreference projectPref = selectByUserAndProject(userId, projectId);
            if (projectPref != null) {
                return projectPref;
            }
        }
        // Fallback 到全局偏好
        return getGlobalByUserId(userId);
    }

    /**
     * 获取用户全局通知偏好，若不存在则创建默认记录
     */
    public NotificationPreference getGlobalByUserId(Long userId) {
        NotificationPreference pref = selectByUserAndProject(userId, null);
        if (pref == null) {
            pref = createDefault(userId, null);
        }
        return pref;
    }

    /**
     * 向后兼容：等同于 getGlobalByUserId
     */
    public NotificationPreference getByUserId(Long userId) {
        return getGlobalByUserId(userId);
    }

    /**
     * 获取用户指定项目的偏好（可能为 null，表示使用全局设置）
     */
    public NotificationPreference getProjectPreference(Long userId, Long projectId) {
        return selectByUserAndProject(userId, projectId);
    }

    /**
     * 列出用户已配置项目级偏好的所有记录
     */
    public List<NotificationPreference> listProjectPreferences(Long userId) {
        return preferenceMapper.selectList(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
                        .isNotNull(NotificationPreference::getProjectId)
                        .orderByAsc(NotificationPreference::getCreatedAt)
        );
    }

    /**
     * 更新用户通知偏好（全局，向后兼容）
     */
    @Transactional
    public NotificationPreference update(Long userId, UpdateNotificationPreferenceDTO dto) {
        return update(userId, null, dto);
    }

    /**
     * 更新用户通知偏好（支持全局或项目级）。
     * 若 projectId 非空且该项目的偏好不存在，则自动创建。
     */
    @Transactional
    public NotificationPreference update(Long userId, Long projectId, UpdateNotificationPreferenceDTO dto) {
        // 校验静音时段一致性：要么同时为 null，要么同时有值
        boolean hasStart = dto.getQuietHoursStart() != null && !dto.getQuietHoursStart().isEmpty();
        boolean hasEnd = dto.getQuietHoursEnd() != null && !dto.getQuietHoursEnd().isEmpty();
        if (hasStart != hasEnd) {
            throw new BusinessException(ErrorCode.QUIET_HOURS_INCOMPLETE);
        }

        NotificationPreference pref;
        if (projectId != null) {
            pref = selectByUserAndProject(userId, projectId);
            if (pref == null) {
                // 创建项目级偏好（以全局偏好为基础）
                pref = createDefault(userId, projectId);
            }
        } else {
            pref = getGlobalByUserId(userId);
        }

        applyUpdate(pref, dto);
        pref.setUpdatedAt(LocalDateTime.now());
        preferenceMapper.updateById(pref);
        return pref;
    }

    /**
     * 删除用户指定项目的偏好（恢复使用全局设置）
     */
    @Transactional
    public void deleteProjectPreference(Long userId, Long projectId) {
        preferenceMapper.delete(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
                        .eq(NotificationPreference::getProjectId, projectId)
        );
    }

    // ===== 私有方法 =====

    private NotificationPreference selectByUserAndProject(Long userId, Long projectId) {
        LambdaQueryWrapper<NotificationPreference> wrapper = new LambdaQueryWrapper<NotificationPreference>()
                .eq(NotificationPreference::getUserId, userId);
        if (projectId != null) {
            wrapper.eq(NotificationPreference::getProjectId, projectId);
        } else {
            wrapper.isNull(NotificationPreference::getProjectId);
        }
        return preferenceMapper.selectOne(wrapper);
    }

    private void applyUpdate(NotificationPreference pref, UpdateNotificationPreferenceDTO dto) {
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
    }

    /**
     * 创建默认偏好记录（使用全局通知管理中配置的默认值）
     */
    private NotificationPreference createDefault(Long userId, Long projectId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        pref.setProjectId(projectId);
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

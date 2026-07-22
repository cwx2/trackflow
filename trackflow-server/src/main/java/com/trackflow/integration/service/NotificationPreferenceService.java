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
import java.util.*;
import java.util.stream.Collectors;

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
     * 检查用户是否希望接收自己操作触发的通知。
     * <p>
     * 支持"项目级覆盖全局"逻辑：
     * 1. 若 projectId 非空且存在项目级偏好记录 → 使用项目级偏好
     * 2. 否则 → 使用全局偏好
     *
     * @param userId    用户 ID
     * @param projectId 项目 ID（可选，NULL 表示使用全局偏好）
     * @return true 表示用户希望接收自己操作产生的通知
     */
    public boolean isNotifyOwnChanges(Long userId, Long projectId) {
        try {
            NotificationPreference pref = getApplicable(userId, projectId);
            return Boolean.TRUE.equals(pref.getNotifyOwnChanges());
        } catch (Exception e) {
            log.warn("[NotificationPreference] 查询 notifyOwnChanges 失败: userId={}, projectId={}, 默认不通知",
                    userId, projectId, e);
            return false; // 查询失败时默认不通知自己（保守策略）
        }
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
     * 检查用户对指定事件类型的邮件通知是否启用。
     * <p>
     * 判断顺序：
     * 1. 全局 emailEnabled 总开关必须开启
     * 2. 该事件类型的 per-event 邮件开关必须开启
     * <p>
     * 支持"项目级覆盖全局"逻辑。
     *
     * @param userId    用户 ID
     * @param eventType 通知事件类型
     * @param projectId 项目 ID（可为 null）
     * @return true 表示该事件类型应发送邮件
     */
    public boolean isEmailEnabledForEvent(Long userId, NotificationEventType eventType, Long projectId) {
        try {
            NotificationPreference pref = getApplicable(userId, projectId);
            // 总开关未开 → 不发邮件
            if (!Boolean.TRUE.equals(pref.getEmailEnabled())) {
                return false;
            }
            // per-event 邮件开关
            return eventType.isEmailEnabled(pref);
        } catch (Exception e) {
            log.warn("[NotificationPreference] 查询邮件偏好失败: userId={}, eventType={}, projectId={}, 默认不发邮件",
                    userId, eventType, projectId, e);
            return false; // 邮件查询失败时默认不发（保守策略，避免骚扰用户）
        }
    }

    /**
     * 检查用户偏好是否允许为特定通知类型发送邮件（直接传入偏好对象，避免重复查询）。
     * <p>
     * 用于批量场景（如 NotificationMailScheduler），调用方已批量查询了偏好。
     *
     * @param pref      用户偏好（可为 null，null 视为默认不发邮件）
     * @param eventType 通知事件类型
     * @return true 表示该事件类型应发送邮件
     */
    public static boolean isEmailEnabledForEvent(NotificationPreference pref, NotificationEventType eventType) {
        if (pref == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(pref.getEmailEnabled())) {
            return false;
        }
        return eventType.isEmailEnabled(pref);
    }

    /**
     * 批量检查哪些用户对指定事件类型的通知偏好启用（批量通知发送场景）。
     * <p>
     * 对每个用户执行"项目级覆盖全局"逻辑，返回允许接收通知的用户 ID 集合。
     * 注意：查询失败的用户默认视为启用（不阻断通知）。
     *
     * @param userIds    待检查的用户 ID 集合
     * @param eventType  通知事件类型
     * @param projectId  项目 ID（可为 null）
     * @return 允许接收此类通知的用户 ID 集合
     */
    public Set<Long> getEnabledUserIds(Collection<Long> userIds, NotificationEventType eventType, Long projectId) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }

        // 批量查询项目级偏好（如果 projectId 非空）
        Map<Long, NotificationPreference> projectPrefMap = Collections.emptyMap();
        if (projectId != null) {
            List<NotificationPreference> projectPrefs = preferenceMapper.selectList(
                    new LambdaQueryWrapper<NotificationPreference>()
                            .in(NotificationPreference::getUserId, userIds)
                            .eq(NotificationPreference::getProjectId, projectId)
            );
            projectPrefMap = projectPrefs.stream()
                    .collect(Collectors.toMap(NotificationPreference::getUserId, p -> p));
        }

        // 批量查询全局偏好
        List<NotificationPreference> globalPrefs = preferenceMapper.selectList(
                new LambdaQueryWrapper<NotificationPreference>()
                        .in(NotificationPreference::getUserId, userIds)
                        .isNull(NotificationPreference::getProjectId)
        );
        Map<Long, NotificationPreference> globalPrefMap = globalPrefs.stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId, p -> p));

        Set<Long> enabledUsers = new HashSet<>();
        for (Long userId : userIds) {
            try {
                // 项目级优先
                NotificationPreference pref = projectPrefMap.get(userId);
                if (pref == null) {
                    pref = globalPrefMap.get(userId);
                }
                // 无偏好记录 → 默认启用
                if (pref == null || eventType.isEnabled(pref)) {
                    enabledUsers.add(userId);
                }
            } catch (Exception e) {
                // 查询失败默认发送
                enabledUsers.add(userId);
            }
        }
        return enabledUsers;
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
     * 批量查询多个用户的全局通知偏好（批量邮件分发场景）。
     * 不自动创建默认记录——不在列表中的用户视为使用默认配置。
     *
     * @param userIds 用户 ID 集合
     * @return 全局偏好列表（仅已存在记录的用户）
     */
    public List<NotificationPreference> listGlobalByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return preferenceMapper.selectList(
                new LambdaQueryWrapper<NotificationPreference>()
                        .in(NotificationPreference::getUserId, userIds)
                        .isNull(NotificationPreference::getProjectId)
        );
    }

    /**
     * 更新用户通知偏好（全局，向后兼容）
     */
    @Transactional(rollbackFor = Exception.class)
        public NotificationPreference update(Long userId, UpdateNotificationPreferenceDTO dto) {
        return update(userId, null, dto);
    }

    /**
     * 更新用户通知偏好（支持全局或项目级）。
     * 若 projectId 非空且该项目的偏好不存在，则自动创建。
     */
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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
        if (dto.getOnIssueUpdated() != null) {
            pref.setOnIssueUpdated(dto.getOnIssueUpdated());
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
        if (dto.getOnDueDate() != null) {
            pref.setOnDueDate(dto.getOnDueDate());
        }
        if (dto.getOnOverdue() != null) {
            pref.setOnOverdue(dto.getOnOverdue());
        }
        if (dto.getDueDateAdvanceDays() != null) {
            pref.setDueDateAdvanceDays(dto.getDueDateAdvanceDays());
        }
        if (dto.getNotifyOwnChanges() != null) {
            pref.setNotifyOwnChanges(dto.getNotifyOwnChanges());
        }
        if (dto.getEmailEnabled() != null) {
            pref.setEmailEnabled(dto.getEmailEnabled());
        }
        // Per-event 邮件渠道控制
        if (dto.getEmailOnIssueAssigned() != null) {
            pref.setEmailOnIssueAssigned(dto.getEmailOnIssueAssigned());
        }
        if (dto.getEmailOnIssueStatusChanged() != null) {
            pref.setEmailOnIssueStatusChanged(dto.getEmailOnIssueStatusChanged());
        }
        if (dto.getEmailOnIssueCommented() != null) {
            pref.setEmailOnIssueCommented(dto.getEmailOnIssueCommented());
        }
        if (dto.getEmailOnMentioned() != null) {
            pref.setEmailOnMentioned(dto.getEmailOnMentioned());
        }
        if (dto.getEmailOnIssueResolved() != null) {
            pref.setEmailOnIssueResolved(dto.getEmailOnIssueResolved());
        }
        if (dto.getEmailOnIssueUpdated() != null) {
            pref.setEmailOnIssueUpdated(dto.getEmailOnIssueUpdated());
        }
        if (dto.getEmailOnSprintStarted() != null) {
            pref.setEmailOnSprintStarted(dto.getEmailOnSprintStarted());
        }
        if (dto.getEmailOnSprintCompleted() != null) {
            pref.setEmailOnSprintCompleted(dto.getEmailOnSprintCompleted());
        }
        if (dto.getEmailOnProjectMemberChanged() != null) {
            pref.setEmailOnProjectMemberChanged(dto.getEmailOnProjectMemberChanged());
        }
        if (dto.getEmailOnProjectLifecycle() != null) {
            pref.setEmailOnProjectLifecycle(dto.getEmailOnProjectLifecycle());
        }
        if (dto.getEmailOnDueDate() != null) {
            pref.setEmailOnDueDate(dto.getEmailOnDueDate());
        }
        if (dto.getEmailOnOverdue() != null) {
            pref.setEmailOnOverdue(dto.getEmailOnOverdue());
        }
        if (dto.getEmailOnWatchedUpdated() != null) {
            pref.setEmailOnWatchedUpdated(dto.getEmailOnWatchedUpdated());
        }
        // 静音时段允许设置为 null（清除）
        pref.setQuietHoursStart(dto.getQuietHoursStart());
        pref.setQuietHoursEnd(dto.getQuietHoursEnd());

        // Watched 通知开关
        if (dto.getOnWatchedUpdated() != null) {
            pref.setOnWatchedUpdated(dto.getOnWatchedUpdated());
        }

        // 自动关注行为配置
        if (dto.getAutoWatchOnCreate() != null) {
            pref.setAutoWatchOnCreate(dto.getAutoWatchOnCreate());
        }
        if (dto.getAutoWatchOnComment() != null) {
            pref.setAutoWatchOnComment(dto.getAutoWatchOnComment());
        }
        if (dto.getAutoWatchOnUpdate() != null) {
            pref.setAutoWatchOnUpdate(dto.getAutoWatchOnUpdate());
        }
        if (dto.getAutoWatchOnAssign() != null) {
            pref.setAutoWatchOnAssign(dto.getAutoWatchOnAssign());
        }
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
        pref.setOnIssueUpdated(getDefaultBool("notification.default_on_issue_updated", true));
        pref.setOnSprintStarted(getDefaultBool("notification.default_on_sprint_started", false));
        pref.setOnSprintCompleted(getDefaultBool("notification.default_on_sprint_completed", false));
        pref.setOnProjectMemberChanged(getDefaultBool("notification.default_on_project_member_changed", true));
        pref.setOnProjectLifecycle(getDefaultBool("notification.default_on_project_lifecycle", true));
        pref.setOnDueDate(getDefaultBool("notification.default_on_due_date", true));
        pref.setOnOverdue(getDefaultBool("notification.default_on_overdue", true));
        pref.setDueDateAdvanceDays(getDefaultInt("notification.default_due_date_advance_days", 1));
        pref.setNotifyOwnChanges(getDefaultBool("notification.default_notify_own_changes", false));
        pref.setEmailEnabled(getDefaultBool("notification.default_email_enabled", false));
        // Per-event 邮件渠道默认值：重要事件默认开启，普通事件默认关闭
        pref.setEmailOnIssueAssigned(true);
        pref.setEmailOnIssueStatusChanged(true);
        pref.setEmailOnIssueCommented(false);
        pref.setEmailOnMentioned(true);
        pref.setEmailOnIssueResolved(true);
        pref.setEmailOnIssueUpdated(false);
        pref.setEmailOnSprintStarted(false);
        pref.setEmailOnSprintCompleted(false);
        pref.setEmailOnProjectMemberChanged(true);
        pref.setEmailOnProjectLifecycle(false);
        pref.setEmailOnDueDate(true);
        pref.setEmailOnOverdue(true);
        pref.setEmailOnWatchedUpdated(false);
        // Watched 通知开关默认开启
        pref.setOnWatchedUpdated(true);
        // 自动关注行为默认值
        pref.setAutoWatchOnCreate(true);
        pref.setAutoWatchOnComment(true);
        pref.setAutoWatchOnUpdate(false);
        pref.setAutoWatchOnAssign(true);
        pref.setCreatedAt(LocalDateTime.now());
        pref.setUpdatedAt(LocalDateTime.now());
        preferenceMapper.insert(pref);
        return pref;
    }

    private boolean getDefaultBool(String key, boolean fallback) {
        String value = systemSettingService.getSettingValue(key, String.valueOf(fallback));
        return Boolean.parseBoolean(value);
    }

    private int getDefaultInt(String key, int fallback) {
        try {
            return Integer.parseInt(systemSettingService.getSettingValue(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

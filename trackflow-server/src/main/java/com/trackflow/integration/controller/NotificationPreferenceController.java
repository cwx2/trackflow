package com.trackflow.integration.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.converter.NotificationPreferenceConverter;
import com.trackflow.integration.dto.UpdateNotificationPreferenceDTO;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.service.NotificationPreferenceService;
import com.trackflow.integration.vo.NotificationPreferenceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notification-preferences")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final NotificationPreferenceConverter preferenceConverter;

    /**
     * 获取当前用户的全局通知偏好
     */
    @GetMapping
    public R<NotificationPreferenceVO> getGlobal() {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference pref = preferenceService.getGlobalByUserId(userId);
        return R.ok(preferenceConverter.toVO(pref));
    }

    /**
     * 更新当前用户的全局通知偏好
     */
    @PutMapping
    public R<NotificationPreferenceVO> updateGlobal(@Valid @RequestBody UpdateNotificationPreferenceDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference pref = preferenceService.update(userId, dto);
        return R.ok(preferenceConverter.toVO(pref));
    }

    /**
     * 列出当前用户已配置的所有项目级偏好
     */
    @GetMapping("/projects")
    public R<List<NotificationPreferenceVO>> listProjectPreferences() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<NotificationPreference> list = preferenceService.listProjectPreferences(userId);
        return R.ok(preferenceConverter.toVOList(list));
    }

    /**
     * 获取当前用户指定项目的偏好（若不存在则返回 null，表示使用全局设置）
     */
    @GetMapping("/projects/{projectId}")
    public R<NotificationPreferenceVO> getProjectPreference(@PathVariable Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference pref = preferenceService.getProjectPreference(userId, projectId);
        if (pref == null) {
            return R.ok(null);
        }
        return R.ok(preferenceConverter.toVO(pref));
    }

    /**
     * 设置/更新当前用户指定项目的通知偏好
     */
    @PutMapping("/projects/{projectId}")
    public R<NotificationPreferenceVO> updateProjectPreference(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateNotificationPreferenceDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference pref = preferenceService.update(userId, projectId, dto);
        return R.ok(preferenceConverter.toVO(pref));
    }

    /**
     * 删除当前用户指定项目的偏好（恢复使用全局设置）
     */
    @DeleteMapping("/projects/{projectId}")
    public R<Void> deleteProjectPreference(@PathVariable Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        preferenceService.deleteProjectPreference(userId, projectId);
        return R.ok();
    }
}

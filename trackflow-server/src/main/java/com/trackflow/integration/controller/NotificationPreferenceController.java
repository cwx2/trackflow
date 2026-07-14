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

@RestController
@RequestMapping("/api/v1/notification-preferences")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final NotificationPreferenceConverter preferenceConverter;

    /**
     * 获取当前用户的通知偏好
     */
    @GetMapping
    public R<NotificationPreferenceVO> get() {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference pref = preferenceService.getByUserId(userId);
        return R.ok(preferenceConverter.toVO(pref));
    }

    /**
     * 更新当前用户的通知偏好
     */
    @PutMapping
    public R<NotificationPreferenceVO> update(@Valid @RequestBody UpdateNotificationPreferenceDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationPreference pref = preferenceService.update(userId, dto);
        return R.ok(preferenceConverter.toVO(pref));
    }
}

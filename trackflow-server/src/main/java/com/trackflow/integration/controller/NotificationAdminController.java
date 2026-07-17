package com.trackflow.integration.controller;

import com.trackflow.common.model.R;
import com.trackflow.integration.dto.UpdateEmailConfigDTO;
import com.trackflow.integration.dto.UpdateNotificationSettingsDTO;
import com.trackflow.integration.service.EmailConfigService;
import com.trackflow.integration.service.NotificationAdminService;
import com.trackflow.integration.vo.EmailConfigVO;
import com.trackflow.integration.vo.NotificationSettingsVO;
import com.trackflow.integration.vo.NotificationStatsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 通知管理后台接口（管理员专用）
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;
    private final EmailConfigService emailConfigService;

    /**
     * 获取全局通知设置
     */
    @GetMapping("/settings")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<NotificationSettingsVO> getSettings() {
        return R.ok(notificationAdminService.getSettings());
    }

    /**
     * 更新全局通知设置
     */
    @PutMapping("/settings")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<NotificationSettingsVO> updateSettings(@RequestBody UpdateNotificationSettingsDTO dto) {
        return R.ok(notificationAdminService.updateSettings(dto));
    }

    /**
     * 获取通知统计概览
     */
    @GetMapping("/stats")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<NotificationStatsVO> getStats() {
        return R.ok(notificationAdminService.getStats());
    }

    /**
     * 获取邮件服务器配置（密码脱敏）
     */
    @GetMapping("/email-config")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<EmailConfigVO> getEmailConfig() {
        return R.ok(emailConfigService.getConfig());
    }

    /**
     * 更新邮件服务器配置
     */
    @PutMapping("/email-config")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<EmailConfigVO> updateEmailConfig(@Valid @RequestBody UpdateEmailConfigDTO dto) {
        return R.ok(emailConfigService.updateConfig(dto));
    }
}

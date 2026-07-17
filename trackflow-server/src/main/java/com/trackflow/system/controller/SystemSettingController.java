package com.trackflow.system.controller;

import com.trackflow.common.model.R;
import com.trackflow.system.dto.UpdateTimeTrackingSettingsDTO;
import com.trackflow.system.service.SystemSettingService;
import com.trackflow.system.vo.TimeTrackingSettingsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统设置控制器
 */
@RestController
@RequestMapping("/api/v1/system/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService settingService;

    /**
     * 获取时间追踪设置（所有认证用户可读取，前端需要用于工时换算）
     */
    @GetMapping("/time-tracking")
    public R<TimeTrackingSettingsVO> getTimeTrackingSettings() {
        return R.ok(settingService.getTimeTrackingSettings());
    }

    /**
     * 更新时间追踪设置（需要系统管理权限）
     */
    @PutMapping("/time-tracking")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<TimeTrackingSettingsVO> updateTimeTrackingSettings(
            @Valid @RequestBody UpdateTimeTrackingSettingsDTO dto) {
        return R.ok(settingService.updateTimeTrackingSettings(dto));
    }
}

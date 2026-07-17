package com.trackflow.system.controller;

import com.trackflow.common.model.R;
import com.trackflow.timeentry.service.TimeEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统管理员维护操作
 * 提供数据校准、自愈等管理员专属功能
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TimeEntryService timeEntryService;

    /**
     * 全量校准所有 issue 的 spent_hours 字段
     * 将 issue.spent_hours 与 time_entry 表的实际聚合值对齐
     * 用于修复因异常/并发导致的数据不一致
     */
    @PostMapping("/recalculate-spent-hours")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Map<String, Object>> recalculateSpentHours() {
        int affectedRows = timeEntryService.recalculateAllSpentHours();
        return R.ok(Map.of(
                "affectedRows", affectedRows,
                "message", "spent_hours 全量校准完成"
        ));
    }
}

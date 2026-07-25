package com.trackflow.system.controller;

import com.trackflow.common.model.R;
import com.trackflow.issue.service.StatusCacheHelper;
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
    private final StatusCacheHelper statusCacheHelper;

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

    /**
     * 手动刷新工单状态缓存（StatusCacheHelper）
     * <p>
     * 适用场景：通过 Flyway 迁移脚本手动修改了 issue_status 表（如更新 is_closed 标志）后，
     * 需要所有实例立即感知最新状态定义，可调用此接口强制清除本实例的本地缓存。
     * <p>
     * 注意：单实例部署下，此接口会清除当前节点缓存；
     * 多实例部署下，需在每个节点分别调用（或配合 Redis Pub/Sub 升级为全局通知）。
     */
    @PostMapping("/refresh-status-cache")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Map<String, Object>> refreshStatusCache() {
        statusCacheHelper.invalidate();
        return R.ok(Map.of(
                "message", "工单状态缓存已清除，下次查询将从数据库重新加载"
        ));
    }
}

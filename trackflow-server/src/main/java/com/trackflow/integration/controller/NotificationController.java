package com.trackflow.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.entity.NotificationCategory;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.integration.vo.NotificationVO;
import com.trackflow.integration.service.MutedThreadService;
import com.trackflow.integration.vo.MutedThreadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final MutedThreadService mutedThreadService;

    @GetMapping
    public R<PageResult<NotificationVO>> list(
            @RequestParam(value = "unreadOnly", required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(value = "category", required = false) NotificationCategory category,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Notification> pageObj = PageHelper.buildPage(page, pageSize);
        PageResult<NotificationVO> pageResult = notificationService.listWithActor(userId, unreadOnly, category, projectId, pageObj);
        return R.ok(pageResult);
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    /**
     * 获取各分类的未读计数（用于标签页 badge 展示）
     */
    @GetMapping("/unread-count-by-category")
    public R<Map<String, Long>> unreadCountByCategory() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(notificationService.unreadCountByCategory(userId));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.markRead(id, userId);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllRead(userId);
        return R.ok();
    }

    /**
     * 删除单条通知（所有权校验：只能删除自己的通知）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.delete(id, userId);
        return R.ok();
    }

    /**
     * 清除当前用户所有已读通知
     */
    @DeleteMapping("/read")
    public R<Map<String, Integer>> deleteAllRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        int deleted = notificationService.deleteAllRead(userId);
        return R.ok(Map.of("deleted", deleted));
    }

    // ===== 线程静音 API =====

    /**
     * 静音指定资源的通知（静音后不再收到该资源的更新通知，@提及除外）
     */
    @PostMapping("/mute")
    public R<Void> muteThread(@RequestParam String resourceType, @RequestParam Long resourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        mutedThreadService.mute(userId, resourceType, resourceId);
        return R.ok();
    }

    /**
     * 取消静音
     */
    @DeleteMapping("/mute")
    public R<Void> unmuteThread(@RequestParam String resourceType, @RequestParam Long resourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        mutedThreadService.unmute(userId, resourceType, resourceId);
        return R.ok();
    }

    /**
     * 检查指定资源是否已静音
     */
    @GetMapping("/mute/check")
    public R<Map<String, Boolean>> checkMuted(@RequestParam String resourceType, @RequestParam Long resourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean muted = mutedThreadService.isMuted(userId, resourceType, resourceId);
        return R.ok(Map.of("muted", muted));
    }

    /**
     * 获取当前用户所有已静音线程列表
     */
    @GetMapping("/muted-threads")
    public R<List<MutedThreadVO>> listMutedThreads() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(mutedThreadService.listMutedThreads(userId));
    }
}

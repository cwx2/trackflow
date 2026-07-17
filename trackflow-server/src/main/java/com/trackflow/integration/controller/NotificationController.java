package com.trackflow.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.integration.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public R<PageResult<NotificationVO>> list(
            @RequestParam(value = "unreadOnly", required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Notification> pageObj = PageHelper.buildPage(page, pageSize);
        PageResult<NotificationVO> pageResult = notificationService.listWithActor(userId, unreadOnly, pageObj);
        return R.ok(pageResult);
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
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
    public R<Void> delete(@PathVariable Long id) {
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
}

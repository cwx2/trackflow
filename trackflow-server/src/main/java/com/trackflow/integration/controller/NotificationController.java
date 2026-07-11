package com.trackflow.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.converter.NotificationConverter;
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
    private final NotificationConverter notificationConverter;

    @GetMapping
    public R<PageResult<NotificationVO>> list(
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Notification> pageObj = PageHelper.buildPage(page, pageSize);
        Page<Notification> result = notificationService.list(userId, unreadOnly, pageObj);
        PageResult<NotificationVO> pageResult = new PageResult<>(
                notificationConverter.toVOList(result.getRecords()), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllRead(userId);
        return R.ok();
    }
}

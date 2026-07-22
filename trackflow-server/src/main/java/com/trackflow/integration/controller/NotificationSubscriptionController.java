package com.trackflow.integration.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.dto.CreateSubscriptionDTO;
import com.trackflow.integration.dto.UpdateSubscriptionEventsDTO;
import com.trackflow.integration.service.NotificationSubscriptionService;
import com.trackflow.integration.vo.NotificationSubscriptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知订阅管理 API。
 * 用户可创建、编辑、删除基于标签或保存搜索的通知订阅规则。
 */
@RestController
@RequestMapping("/api/v1/notification-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationSubscriptionController {

    private final NotificationSubscriptionService subscriptionService;

    /**
     * 获取当前用户的所有订阅（包括默认订阅和自定义订阅）
     */
    @GetMapping
    public R<List<NotificationSubscriptionVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(subscriptionService.listByUser(userId));
    }

    /**
     * 创建自定义订阅
     */
    @PostMapping
    public R<NotificationSubscriptionVO> create(@Valid @RequestBody CreateSubscriptionDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(subscriptionService.create(userId, dto));
    }

    /**
     * 更新订阅的事件配置
     */
    @PutMapping("/{id}")
    public R<NotificationSubscriptionVO> update(@PathVariable("id") Long id,
                                                @Valid @RequestBody UpdateSubscriptionEventsDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(subscriptionService.updateEvents(userId, id, dto));
    }

    /**
     * 删除订阅（默认订阅不可删除）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        subscriptionService.delete(userId, id);
        return R.ok();
    }
}

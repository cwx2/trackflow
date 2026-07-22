package com.trackflow.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.converter.WebhookConverter;
import com.trackflow.integration.dto.CreateWebhookDTO;
import com.trackflow.integration.dto.UpdateWebhookDTO;
import com.trackflow.integration.entity.Webhook;
import com.trackflow.integration.entity.WebhookLog;
import com.trackflow.integration.service.WebhookService;
import com.trackflow.integration.vo.WebhookLogVO;
import com.trackflow.integration.vo.WebhookVO;
import com.trackflow.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookConverter webhookConverter;
    private final ProjectService projectService;
    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<WebhookVO>> list(@RequestParam("projectId") Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(webhookConverter.toVOList(webhookService.listByProject(projectId)));
    }

    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'webhook:manage')")
    public R<WebhookVO> create(@P("dto") @Valid @RequestBody CreateWebhookDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, dto.getProjectId());

        Webhook webhook = new Webhook();
        webhook.setProjectId(dto.getProjectId());
        webhook.setName(dto.getName());
        webhook.setUrl(dto.getUrl());
        webhook.setSecret(dto.getSecret());
        webhook.setEvents(dto.getEvents());
        webhook.setActive(dto.getActive());

        return R.ok(webhookConverter.toVO(webhookService.create(webhook)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<WebhookVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateWebhookDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Webhook webhook = getWebhookWithPermissionCheck(id, userId);

        if (dto.getName() != null) webhook.setName(dto.getName());
        if (dto.getUrl() != null) webhook.setUrl(dto.getUrl());
        if (dto.getSecret() != null) webhook.setSecret(dto.getSecret());
        if (dto.getEvents() != null) webhook.setEvents(dto.getEvents());
        if (dto.getActive() != null) webhook.setActive(dto.getActive());

        return R.ok(webhookConverter.toVO(webhookService.update(webhook)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        getWebhookWithPermissionCheck(id, userId);
        webhookService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("isAuthenticated()")
    public R<WebhookLogVO> testTrigger(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Webhook webhook = getWebhookWithPermissionCheck(id, userId);
        WebhookLog logEntry = webhookService.testTrigger(webhook);
        return R.ok(webhookConverter.toLogVO(logEntry));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<WebhookLogVO>> getDeliveryLogs(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        getWebhookWithPermissionCheck(id, userId);

        Page<WebhookLog> result = webhookService.getDeliveryLogs(id, page, pageSize);
        List<WebhookLogVO> voList = webhookConverter.toLogVOList(result.getRecords());
        return R.ok(new PageResult<>(voList, result.getTotal(), (int) result.getCurrent(), (int) result.getSize()));
    }

    /**
     * 获取 Webhook 并校验当前用户是否有 webhook:manage 权限
     */
    private Webhook getWebhookWithPermissionCheck(Long webhookId, Long userId) {
        Webhook webhook = webhookService.getById(webhookId);
        if (webhook == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Webhook不存在");
        }
        projectService.assertProjectMember(userId, webhook.getProjectId());
        if (!permissionService.hasPermission(userId, webhook.getProjectId(), "webhook:manage")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无 webhook:manage 权限");
        }
        return webhook;
    }
}

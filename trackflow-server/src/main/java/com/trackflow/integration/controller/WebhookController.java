package com.trackflow.integration.controller;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.converter.WebhookConverter;
import com.trackflow.integration.dto.CreateWebhookDTO;
import com.trackflow.integration.entity.Webhook;
import com.trackflow.integration.service.WebhookService;
import com.trackflow.integration.vo.WebhookVO;
import com.trackflow.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public R<List<WebhookVO>> list(@RequestParam Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(webhookConverter.toVOList(webhookService.listByProject(projectId)));
    }

    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'webhook:manage')")
    public R<WebhookVO> create(@Valid @RequestBody CreateWebhookDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 双重校验：即使权限通过也要确认是项目成员（防止权限缓存和 projectId 不一致）
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

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 查询 webhook 确认归属项目，然后校验用户是该项目成员且有 webhook:manage 权限
        Webhook webhook = webhookService.getById(id);
        if (webhook == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Webhook not found");
        }
        projectService.assertProjectMember(userId, webhook.getProjectId());
        // 额外校验 webhook:manage 权限
        if (!permissionService.hasPermission(userId, webhook.getProjectId(), "webhook:manage")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无 webhook:manage 权限");
        }
        webhookService.delete(id);
        return R.ok();
    }
}

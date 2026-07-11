package com.trackflow.integration.controller;

import com.trackflow.common.model.R;
import com.trackflow.integration.converter.WebhookConverter;
import com.trackflow.integration.entity.Webhook;
import com.trackflow.integration.service.WebhookService;
import com.trackflow.integration.vo.WebhookVO;
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

    @GetMapping
    public R<List<WebhookVO>> list(@RequestParam Long projectId) {
        return R.ok(webhookConverter.toVOList(webhookService.listByProject(projectId)));
    }

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('webhook:manage')")
    public R<WebhookVO> create(@RequestBody Webhook webhook) {
        return R.ok(webhookConverter.toVO(webhookService.create(webhook)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('webhook:manage')")
    public R<Void> delete(@PathVariable Long id) {
        webhookService.delete(id);
        return R.ok();
    }
}

package com.trackflow.automation.trigger;

import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.service.AutomationWorkflowService;
import com.trackflow.auth.security.NoAuthorizationRequired;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation/webhooks")
public class AutomationWebhookController {
    private final AutomationWorkflowService workflowService;
    private final AutomationTriggerService triggerService;
    private final WebhookTokenVerifier webhookTokenVerifier;

    @PostMapping("/{workflowId}")
    @NoAuthorizationRequired(reason = "Webhook 使用工作流专属 Token 和幂等键鉴权")
    public R<Void> receive(@PathVariable Long workflowId,
                           @RequestHeader("X-TrackFlow-Webhook-Token") String token,
                           @RequestHeader("X-Idempotency-Key") String idempotencyKey,
                           @RequestBody(required = false) Map<String, Object> payload) {
        AutomationWorkflow workflow = workflowService.getById(workflowId);
        if (!"published".equals(workflow.getStatus())
                || !Boolean.TRUE.equals(workflow.getRuntimeEnabled())
                || !"webhook".equals(workflow.getTriggerType())) {
            throw BusinessException.notFound("Webhook 工作流不存在、未发布或未启动");
        }
        webhookTokenVerifier.verifyToken(workflow, token);
        triggerService.enqueueWebhook(workflow, "webhook:" + idempotencyKey,
                payload != null ? payload : Map.of());
        return R.ok();
    }
}

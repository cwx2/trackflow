package com.trackflow.automation.trigger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.service.AutomationWorkflowService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation/webhooks")
public class AutomationWebhookController {
    private final AutomationWorkflowService workflowService;
    private final AutomationTriggerService triggerService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{workflowId}")
    public R<Void> receive(@PathVariable Long workflowId,
                           @RequestHeader("X-TrackFlow-Webhook-Token") String token,
                           @RequestHeader("X-Idempotency-Key") String idempotencyKey,
                           @RequestBody(required = false) Map<String, Object> payload) {
        AutomationWorkflow workflow = workflowService.getById(workflowId);
        if (!"published".equals(workflow.getStatus()) || !"webhook".equals(workflow.getTriggerType())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Webhook 工作流不存在或未发布");
        }
        try {
            Map<String, Object> config = objectMapper.readValue(
                    workflow.getTriggerConfig(), new TypeReference<>() {});
            byte[] actual = sha256(token);
            byte[] expected = HexFormat.of().parseHex(String.valueOf(config.get("tokenSha256")));
            if (!MessageDigest.isEqual(actual, expected)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "Webhook token 无效");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Webhook 配置损坏");
        }
        triggerService.enqueueWebhook(workflow, "webhook:" + idempotencyKey,
                payload != null ? payload : Map.of());
        return R.ok();
    }

    private byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
    }
}

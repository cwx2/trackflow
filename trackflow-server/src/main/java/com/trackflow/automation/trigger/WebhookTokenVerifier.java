package com.trackflow.automation.trigger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Webhook Token 验证服务 — 校验入站 webhook 请求的 token 合法性。
 * <p>
 * 从工作流的 triggerConfig JSON 中读取预存的 tokenSha256，
 * 与请求中携带的 token 进行 SHA-256 比对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookTokenVerifier {

    private final ObjectMapper objectMapper;

    /**
     * 验证 webhook token 是否与工作流配置中的预存 hash 匹配。
     *
     * @param workflow 已发布的 webhook 工作流
     * @param token    请求头中携带的明文 token
     * @throws BusinessException 当 token 无效或工作流配置损坏时
     */
    public void verifyToken(AutomationWorkflow workflow, String token) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                    workflow.getTriggerConfig(), new TypeReference<>() {});
            byte[] actual = sha256(token);
            byte[] expected = HexFormat.of().parseHex(String.valueOf(config.get("tokenSha256")));
            if (!MessageDigest.isEqual(actual, expected)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "Webhook token 无效");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[WebhookTokenVerifier] 工作流 triggerConfig 解析失败: workflowId={}, error={}",
                    workflow.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_STATE, "Webhook 配置损坏");
        }
    }

    private byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
    }
}

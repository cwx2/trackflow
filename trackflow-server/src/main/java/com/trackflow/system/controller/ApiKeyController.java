package com.trackflow.system.controller;

import com.trackflow.auth.security.ApiKeyAuthenticationToken;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.converter.ApiKeyConverter;
import com.trackflow.system.dto.CreateApiKeyDTO;
import com.trackflow.system.service.ApiKeyService;
import com.trackflow.system.vo.ApiKeyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyConverter apiKeyConverter;

    /**
     * 创建 API Key
     * <p>
     * 安全约束：
     * 1. 必须已认证
     * 2. 禁止 API Key 认证的请求调用（防止 Key 自我复制）
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> create(@Valid @RequestBody CreateApiKeyDTO dto) {
        // 禁止 API Key 认证的请求创建新 Key（防止自我复制形成持久化后门）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiKeyAuthenticationToken) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "API Key 认证的请求不允许创建新的 API Key");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime expiresAt = dto.getExpiresAt() != null && !dto.getExpiresAt().isBlank()
                ? LocalDateTime.parse(dto.getExpiresAt()) : null;
        Map<String, Object> result = apiKeyService.create(userId, dto.getName(), dto.getPermissions(), expiresAt);
        return R.ok(result);
    }

    /**
     * 获取当前用户的 API Key 列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<ApiKeyVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        var keys = apiKeyService.listByUser(userId);
        keys.forEach(k -> k.setKeyHash(null));
        return R.ok(apiKeyConverter.toVOList(keys));
    }

    /**
     * 撤销 API Key
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> revoke(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        apiKeyService.revoke(id, userId);
        return R.ok();
    }
}

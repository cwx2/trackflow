package com.trackflow.system.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.converter.ApiKeyConverter;
import com.trackflow.system.dto.CreateApiKeyDTO;
import com.trackflow.system.service.ApiKeyService;
import com.trackflow.system.vo.ApiKeyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody CreateApiKeyDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime expiresAt = dto.getExpiresAt() != null && !dto.getExpiresAt().isBlank()
                ? LocalDateTime.parse(dto.getExpiresAt()) : null;
        Map<String, Object> result = apiKeyService.create(userId, dto.getName(), dto.getPermissions(), expiresAt);
        return R.ok(result);
    }

    @GetMapping
    public R<List<ApiKeyVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        var keys = apiKeyService.listByUser(userId);
        keys.forEach(k -> k.setKeyHash(null));
        return R.ok(apiKeyConverter.toVOList(keys));
    }

    @DeleteMapping("/{id}")
    public R<Void> revoke(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        apiKeyService.revoke(id, userId);
        return R.ok();
    }
}

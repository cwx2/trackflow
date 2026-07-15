package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.system.entity.ApiKey;
import com.trackflow.system.mapper.ApiKeyMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * API Key 管理服务
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "tf_";
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyMapper apiKeyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 API Key
     *
     * @return 包含明文 key 的 Map（明文仅此一次返回）
     */
    @Transactional
    public Map<String, Object> create(Long userId, String name, List<String> permissions, LocalDateTime expiresAt) {
        // 生成 key
        String prefix = generateRandomString(PREFIX_LENGTH);
        String secret = generateRandomString(SECRET_LENGTH);
        String plainKey = KEY_PREFIX + prefix + secret;
        String keyHash = BCrypt.hashpw(plainKey, BCrypt.gensalt());

        // 保存
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setName(name);
        apiKey.setKeyHash(keyHash);
        apiKey.setPrefix(KEY_PREFIX + prefix);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setExpiresAt(expiresAt);

        if (permissions != null && !permissions.isEmpty()) {
            try {
                apiKey.setPermissions(objectMapper.writeValueAsString(permissions));
            } catch (JsonProcessingException e) {
                apiKey.setPermissions("[]");
            }
        } else {
            apiKey.setPermissions("[]");
        }

        apiKeyMapper.insert(apiKey);

        return Map.of(
                "id", String.valueOf(apiKey.getId()),
                "name", apiKey.getName(),
                "key", plainKey,  // 仅此一次返回明文
                "prefix", apiKey.getPrefix(),
                "expiresAt", expiresAt != null ? expiresAt.toString() : "",
                "createdAt", apiKey.getCreatedAt().toString()
        );
    }

    /**
     * 获取用户的 API Key 列表（不包含明文）
     */
    public List<ApiKey> listByUser(Long userId) {
        return apiKeyMapper.selectList(
                new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, userId)
                        .orderByDesc(ApiKey::getCreatedAt)
        );
    }

    /**
     * 撤销 API Key
     */
    @Transactional
    public void revoke(Long id, Long userId) {
        ApiKey apiKey = apiKeyMapper.selectById(id);
        if (apiKey == null || !apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "API Key not found");
        }
        apiKeyMapper.deleteById(id);
    }

    private String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }
}

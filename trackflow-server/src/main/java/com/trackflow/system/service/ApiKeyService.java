package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.system.entity.ApiKey;
import com.trackflow.system.entity.SysPermission;
import com.trackflow.system.mapper.ApiKeyMapper;
import com.trackflow.system.mapper.SysPermissionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.system.vo.CreateApiKeyResultVO;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API Key 管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "tf_";
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * prefix 碰撞时最大重试次数（UNIQUE 约束兜底，应用层防御）
     */
    private static final int MAX_PREFIX_RETRY = 3;

    /**
     * 每用户最大 API Key 数量
     */
    private static final int MAX_KEYS_PER_USER = 10;

    private final ApiKeyMapper apiKeyMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SystemAuditService systemAuditService;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

    /**
     * 创建 API Key
     * <p>
     * 安全校验：
     * 1. 用户当前 Key 数量 < MAX_KEYS_PER_USER
     * 2. permissions 列表中每个值必须存在于系统已定义的权限集合中
     *
     * @return 包含明文 key 的 CreateApiKeyResultVO（明文仅此一次返回）
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateApiKeyResultVO create(Long userId, String name, List<String> permissions, LocalDateTime expiresAt) {
        // 校验：Key 数量上限
        long existingCount = apiKeyMapper.selectCount(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getUserId, userId)
        );
        if (existingCount >= MAX_KEYS_PER_USER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "每个用户最多创建 " + MAX_KEYS_PER_USER + " 个 API Key，当前已有 " + existingCount + " 个");
        }

        // 校验：permissions 合法性
        if (permissions != null && !permissions.isEmpty()) {
            validatePermissions(permissions, userId);
        }

        // 生成 key（带重试：极小概率 prefix 碰撞时重新生成，最多 MAX_PREFIX_RETRY 次）
        String plainKey = null;
        ApiKey apiKey = null;

        String permissionsJson;
        if (permissions != null && !permissions.isEmpty()) {
            try {
                permissionsJson = objectMapper.writeValueAsString(permissions);
            } catch (JsonProcessingException e) {
                permissionsJson = "[]";
            }
        } else {
            permissionsJson = "[]";
        }

        for (int attempt = 1; attempt <= MAX_PREFIX_RETRY; attempt++) {
            String prefix = generateRandomString(PREFIX_LENGTH);
            String secret = generateRandomString(SECRET_LENGTH);
            plainKey = KEY_PREFIX + prefix + secret;
            String keyHash = BCrypt.hashpw(plainKey, BCrypt.gensalt());

            apiKey = new ApiKey();
            apiKey.setUserId(userId);
            apiKey.setName(name);
            apiKey.setKeyHash(keyHash);
            apiKey.setPrefix(KEY_PREFIX + prefix);
            apiKey.setCreatedAt(LocalDateTime.now());
            apiKey.setExpiresAt(expiresAt);
            apiKey.setPermissions(permissionsJson);

            try {
                apiKeyMapper.insert(apiKey);
                break; // 插入成功，退出重试循环
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_PREFIX_RETRY) {
                    log.error("API Key prefix 生成碰撞，已重试 {} 次仍失败，userId={}", MAX_PREFIX_RETRY, userId);
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "API Key 生成失败，请重试");
                }
                log.warn("API Key prefix 碰撞（第 {} 次），重新生成，userId={}", attempt, userId);
            }
        }

        // 审计日志
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("key_name", name);
        auditDetails.put("key_prefix", apiKey.getPrefix());
        auditDetails.put("permissions", permissions != null ? permissions : List.of());
        auditDetails.put("expires_at", expiresAt != null ? expiresAt.toString() : "never");
        systemAuditService.log("create_api_key", "api_key", apiKey.getId(), auditDetails);

        // 构建返回 VO（明文 key 仅此一次返回）
        CreateApiKeyResultVO resultVO = new CreateApiKeyResultVO();
        resultVO.setId(String.valueOf(apiKey.getId()));
        resultVO.setName(apiKey.getName());
        resultVO.setKey(plainKey);
        resultVO.setPrefix(apiKey.getPrefix());
        resultVO.setExpiresAt(expiresAt);
        resultVO.setCreatedAt(apiKey.getCreatedAt());

        return resultVO;
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
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, Long userId) {
        ApiKey apiKey = apiKeyMapper.selectById(id);
        if (apiKey == null || !apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "API Key not found");
        }
        apiKeyMapper.deleteById(id);

        // 审计日志
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("key_name", apiKey.getName());
        auditDetails.put("key_prefix", apiKey.getPrefix());
        systemAuditService.log("revoke_api_key", "api_key", id, auditDetails);
    }

    /**
     * 批量删除用户的所有 API Key（用户被禁用时调用）
     *
     * @param userId 用户 ID
     * @return 被删除的 Key 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int revokeAllByUser(Long userId) {
        List<ApiKey> keys = listByUser(userId);
        if (keys.isEmpty()) {
            return 0;
        }

        int count = apiKeyMapper.delete(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getUserId, userId)
        );

        // 审计日志
        List<String> prefixes = keys.stream().map(ApiKey::getPrefix).collect(Collectors.toList());
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("reason", "user_disabled");
        auditDetails.put("revoked_count", count);
        auditDetails.put("key_prefixes", prefixes);
        systemAuditService.log("revoke_all_api_keys", "api_key", userId, auditDetails);

        log.info("用户 {} 被禁用，已批量吊销 {} 个 API Key", userId, count);
        return count;
    }

    /**
     * 校验 permissions 列表合法性：
     * 1. 每个值必须存在于 sys_permission 表中
     * 2. 用户必须实际持有这些权限（最小权限原则）
     *    - system:admin 用户可以指定任何已定义的权限
     *    - 普通用户只能指定自己拥有的权限（全局 + 任一项目）
     */
    private void validatePermissions(List<String> permissions, Long userId) {
        // CHECK 1: permissions 全部存在于 sys_permission 表
        List<SysPermission> allPermissions = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getEnabled, true)
        );
        Set<String> validCodes = allPermissions.stream()
                .map(SysPermission::getCode)
                .collect(Collectors.toSet());

        List<String> invalidPermissions = permissions.stream()
                .filter(p -> !validCodes.contains(p))
                .collect(Collectors.toList());

        if (!invalidPermissions.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "以下权限代码不存在：" + String.join(", ", invalidPermissions));
        }

        // CHECK 2: 用户必须实际持有这些权限
        // system:admin 拥有所有权限，无需逐项校验
        if (permissionService.isSystemAdmin(userId)) {
            return;
        }

        // 获取用户的全局权限
        Set<String> userGlobalPerms = permissionService.getPermissions(userId);

        // 找出用户全局权限中不包含的 permission，再检查项目级
        List<String> exceededPerms = permissions.stream()
                .filter(p -> !userGlobalPerms.contains(p)
                        && !permissionService.hasPermissionInAnyProject(userId, p))
                .collect(Collectors.toList());

        if (!exceededPerms.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "不能为 API Key 指定您未拥有的权限：" + String.join(", ", exceededPerms));
        }
    }

    private String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }
}

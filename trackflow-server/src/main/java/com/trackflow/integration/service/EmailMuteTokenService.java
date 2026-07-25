package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.integration.entity.EmailMuteToken;
import com.trackflow.integration.mapper.EmailMuteTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 邮件静音 token 服务。
 * <p>
 * 为每封邮件生成唯一 token，嵌入"一键静音此工单"链接中。
 * 用户点击链接后，后端验证 token 有效性并调用 MutedThreadService 完成静音。
 * <p>
 * token 有效期 30 天，支持重复点击（幂等操作）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailMuteTokenService {

    private static final int TOKEN_EXPIRE_DAYS = 30;

    private final EmailMuteTokenMapper emailMuteTokenMapper;
    private final MutedThreadService mutedThreadService;

    /**
     * 为指定用户和资源生成一个新的邮件静音 token。
     * <p>
     * 每次发邮件都生成新 token，旧 token 保留（仍然有效直到过期）。
     *
     * @param userId       目标用户 ID
     * @param resourceType 资源类型（如 "issue"）
     * @param resourceId   资源 ID
     * @return 生成的 token 字符串（UUID v4，无连字符）
     */
    @Transactional(rollbackFor = Exception.class)
    public String generateToken(Long userId, String resourceType, Long resourceId) {
        String token = UUID.randomUUID().toString().replace("-", "");

        EmailMuteToken entity = new EmailMuteToken();
        entity.setToken(token);
        entity.setUserId(userId);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusDays(TOKEN_EXPIRE_DAYS));

        emailMuteTokenMapper.insert(entity);
        log.debug("[EmailMuteToken] 生成 token: userId={}, resourceType={}, resourceId={}", userId, resourceType, resourceId);
        return token;
    }

    /**
     * 通过 token 执行静音操作。
     * <p>
     * 验证 token 有效性（存在且未过期），然后静音对应资源的通知。
     * 支持幂等：资源已静音时不报错。
     *
     * @param token 邮件中的静音 token
     * @throws BusinessException 当 token 无效或已过期时
     */
    @Transactional(rollbackFor = Exception.class)
    public MuteResult muteViaToken(String token) {
        EmailMuteToken muteToken = emailMuteTokenMapper.selectOne(
                new LambdaQueryWrapper<EmailMuteToken>()
                        .eq(EmailMuteToken::getToken, token)
        );

        if (muteToken == null) {
            log.warn("[EmailMuteToken] token 不存在: {}", token);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "链接无效或已失效");
        }

        if (muteToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("[EmailMuteToken] token 已过期: token={}, expiresAt={}", token, muteToken.getExpiresAt());
            throw new BusinessException(ErrorCode.INVALID_STATE, "链接已过期，请在系统中手动管理通知设置");
        }

        // 记录使用时间（仅第一次，幂等操作不更新）
        if (muteToken.getUsedAt() == null) {
            muteToken.setUsedAt(LocalDateTime.now());
            emailMuteTokenMapper.updateById(muteToken);
        }

        // 执行静音（幂等：已静音不报错）
        mutedThreadService.mute(muteToken.getUserId(), muteToken.getResourceType(), muteToken.getResourceId());

        log.info("[EmailMuteToken] 通过邮件链接静音成功: userId={}, resourceType={}, resourceId={}",
                muteToken.getUserId(), muteToken.getResourceType(), muteToken.getResourceId());

        return new MuteResult(muteToken.getUserId(), muteToken.getResourceType(), muteToken.getResourceId());
    }

    /**
     * 静音操作结果，用于构建重定向 URL。
     */
    public record MuteResult(Long userId, String resourceType, Long resourceId) {}
}

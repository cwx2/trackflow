package com.trackflow.integration.service;

import com.trackflow.common.util.EncryptionUtils;
import com.trackflow.integration.dto.UpdateEmailConfigDTO;
import com.trackflow.integration.vo.EmailConfigVO;
import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮件服务器配置服务
 * 使用 system_setting 表存储 SMTP 配置，密码字段加密
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailConfigService {

    private final SystemSettingService systemSettingService;

    private static final String PREFIX = "email.";
    private static final String CATEGORY = "email";
    private static final String PASSWORD_MASK = "******";

    @Value("${trackflow.encryption.key:dHJhY2tmbG93LWFlczI1Ni1lbmNyeXB0LWtleTAxMjM=}")
    private String encryptionKey;

    /**
     * 获取邮件配置（密码脱敏）
     */
    public EmailConfigVO getConfig() {
        EmailConfigVO vo = new EmailConfigVO();

        vo.setHost(getSetting("host", ""));
        vo.setPort(getIntSetting("port", 587));
        vo.setProtocol(getSetting("protocol", "starttls"));
        vo.setUsername(getSetting("username", ""));
        vo.setSslEnabled(getBoolSetting("ssl_enabled", false));
        vo.setFromAddress(getSetting("from_address", ""));
        vo.setReplyToAddress(getSetting("reply_to_address", ""));

        // 密码脱敏
        String encryptedPassword = getSetting("password", "");
        if (!encryptedPassword.isEmpty()) {
            vo.setPassword(PASSWORD_MASK);
        } else {
            vo.setPassword("");
        }

        // 判断是否已完成配置
        vo.setConfigured(isConfigured(vo));

        return vo;
    }

    /**
     * 更新邮件配置
     */
    @Transactional
    public EmailConfigVO updateConfig(UpdateEmailConfigDTO dto) {
        if (dto.getHost() != null) {
            upsertSetting("host", dto.getHost(), "SMTP 服务器地址");
        }
        if (dto.getPort() != null) {
            upsertSetting("port", String.valueOf(dto.getPort()), "SMTP 端口");
        }
        if (dto.getProtocol() != null) {
            validateProtocol(dto.getProtocol());
            upsertSetting("protocol", dto.getProtocol(), "连接协议（plain/ssl/starttls）");
        }
        if (dto.getUsername() != null) {
            upsertSetting("username", dto.getUsername(), "SMTP 认证用户名");
        }
        if (dto.getPassword() != null) {
            // 空字符串表示清除密码；非空则加密存储
            if (dto.getPassword().isEmpty()) {
                upsertSetting("password", "", "SMTP 密码（加密存储）");
            } else {
                String encrypted = EncryptionUtils.encrypt(dto.getPassword(), encryptionKey);
                upsertSetting("password", encrypted, "SMTP 密码（加密存储）");
            }
        }
        if (dto.getSslEnabled() != null) {
            upsertSetting("ssl_enabled", String.valueOf(dto.getSslEnabled()), "是否启用 SSL");
        }
        if (dto.getFromAddress() != null) {
            upsertSetting("from_address", dto.getFromAddress(), "发件人地址");
        }
        if (dto.getReplyToAddress() != null) {
            upsertSetting("reply_to_address", dto.getReplyToAddress(), "回复地址");
        }

        log.info("[EmailConfig] 邮件服务器配置已更新");
        return getConfig();
    }

    /**
     * 获取解密后的 SMTP 密码（供邮件发送模块使用，不对外暴露）
     */
    public String getDecryptedPassword() {
        String encrypted = getSetting("password", "");
        if (encrypted.isEmpty()) {
            return "";
        }
        return EncryptionUtils.decrypt(encrypted, encryptionKey);
    }

    // === Private helpers ===

    private String getSetting(String shortKey, String defaultValue) {
        return systemSettingService.getSettingValue(PREFIX + shortKey, defaultValue);
    }

    private Integer getIntSetting(String shortKey, int defaultValue) {
        String value = systemSettingService.getSettingValue(PREFIX + shortKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean getBoolSetting(String shortKey, boolean defaultValue) {
        String value = systemSettingService.getSettingValue(PREFIX + shortKey, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    private void upsertSetting(String shortKey, String value, String description) {
        systemSettingService.upsertSetting(PREFIX + shortKey, value, description, CATEGORY);
    }

    private void validateProtocol(String protocol) {
        if (!protocol.equals("plain") && !protocol.equals("ssl") && !protocol.equals("starttls")) {
            throw new IllegalArgumentException("协议类型必须为 plain、ssl 或 starttls");
        }
    }

    private boolean isConfigured(EmailConfigVO vo) {
        return vo.getHost() != null && !vo.getHost().isEmpty()
                && vo.getPort() != null
                && vo.getFromAddress() != null && !vo.getFromAddress().isEmpty();
    }
}

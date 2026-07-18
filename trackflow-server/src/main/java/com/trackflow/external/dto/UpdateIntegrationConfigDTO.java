package com.trackflow.external.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 更新第三方集成配置 DTO
 */
@Data
public class UpdateIntegrationConfigDTO {

    /** 配置键值对（短 key → value，如 {"smtpHost": "smtp.example.com", "smtpPort": "587"}） */
    @NotNull(message = "config 不能为空")
    private Map<String, String> config;
}

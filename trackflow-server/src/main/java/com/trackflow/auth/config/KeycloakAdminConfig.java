package com.trackflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak Admin API 配置
 * <p>
 * 用于后端通过 Keycloak Admin REST API 管理用户（创建/禁用/查询）。
 * 使用 client_credentials 模式获取 access token。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminConfig {

    /** Keycloak 服务器地址 */
    private String serverUrl;

    /** 目标 realm */
    private String realm;

    /** 后端服务账号 client ID */
    private String clientId;

    /** 后端服务账号 client secret */
    private String clientSecret;
}

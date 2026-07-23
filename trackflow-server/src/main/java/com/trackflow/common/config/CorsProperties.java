package com.trackflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置属性
 * <p>
 * 通过 application.yml 中的 trackflow.cors 前缀配置。
 * 开发环境默认允许 localhost:3000，生产环境必须显式配置具体域名。
 * <p>
 * 配置格式：逗号分隔的域名字符串。
 * 例如：trackflow.cors.allowed-origins=http://localhost:3000,https://trackflow.company.com
 *
 * @author TrackFlow
 * @since 1.0
 */
@ConfigurationProperties(prefix = "trackflow.cors")
public class CorsProperties {

    /**
     * 允许的跨域来源，逗号分隔。
     * 不允许使用 "*" 通配符（与 allowCredentials=true 不兼容且不安全）。
     */
    private String allowedOrigins;

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw new IllegalArgumentException(
                    "trackflow.cors.allowed-origins must be configured. " +
                    "For production, set to your frontend domain, e.g.: https://trackflow.company.com");
        }
        if ("*".equals(allowedOrigins.trim())) {
            throw new IllegalArgumentException(
                    "trackflow.cors.allowed-origins cannot use wildcard '*'. " +
                    "Please configure specific domains, e.g.: http://localhost:3000");
        }
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 获取解析后的来源列表
     */
    public List<String> getAllowedOriginsList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

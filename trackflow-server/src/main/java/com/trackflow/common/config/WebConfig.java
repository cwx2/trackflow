package com.trackflow.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 注意：Long→String 全局序列化器已移除。
 * ID 字段的字符串化由 VO 层 String 类型 + MapStruct Converter 处理。
 * <p>
 * CORS 配置已迁移到 SecurityConfig 中通过 CorsConfigurationSource Bean 统一管理，
 * 确保 preflight（OPTIONS）请求在认证过滤器之前被正确处理。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS 配置已迁移至 SecurityConfig（通过 CorsConfigurationSource Bean）
    // 如需其他 Web MVC 配置（如拦截器、格式化器），在此添加
}

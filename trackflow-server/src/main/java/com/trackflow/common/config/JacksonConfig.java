package com.trackflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper 配置
 * <p>
 * 提供 com.fasterxml.jackson.databind.ObjectMapper Bean（兼容 MyBatis-Plus 等第三方库）。
 * <p>
 * 注意：Long→String 全局序列化模块已移除。
 * ID 字段的字符串化由 VO 层声明 String 类型 + MapStruct Converter 自动转换处理。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

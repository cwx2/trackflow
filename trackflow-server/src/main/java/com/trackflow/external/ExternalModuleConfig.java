package com.trackflow.external;

import com.trackflow.external.email.EmailAdapter;
import com.trackflow.external.migration.MigrationAdapter;
import com.trackflow.external.sug.SugAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 第三方集成模块配置。
 * <p>
 * 注册所有适配器为 Spring Bean。
 * 各适配器默认 disabled，需要在 application.yml 中启用。
 */
@Configuration
public class ExternalModuleConfig {

    @Bean
    public EmailAdapter emailAdapter() {
        return new EmailAdapter();
    }

    @Bean
    public SugAdapter sugAdapter() {
        return new SugAdapter();
    }

    @Bean
    public MigrationAdapter migrationAdapter() {
        return new MigrationAdapter();
    }
}

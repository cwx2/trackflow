package com.trackflow.common.config;

import com.trackflow.common.aspect.AuditLogAdvisor;
import com.trackflow.common.aspect.DistributedLockAdvisor;
import com.trackflow.common.service.DistributedLockService;
import com.trackflow.system.service.SystemAuditService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;

import java.util.concurrent.Executor;

/**
 * AOP Advisor 配置——将自定义 Advisor 注册为 ROLE_INFRASTRUCTURE。
 * <p>
 * Spring Boot 4 + @EnableMethodSecurity 默认使用 InfrastructureAdvisorAutoProxyCreator，
 * 它仅处理 ROLE_INFRASTRUCTURE 的 Advisor bean。通过 @Role(ROLE_INFRASTRUCTURE) 的 @Bean
 * 声明，使 AuditLogAdvisor 和 DistributedLockAdvisor 被正确识别并应用到 Service 代理中。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Configuration
public class AopConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AuditLogAdvisor auditLogAdvisor(@Lazy SystemAuditService auditService,
                                           @Lazy @org.springframework.beans.factory.annotation.Qualifier("notificationExecutor") Executor executor) {
        return new AuditLogAdvisor(auditService, executor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DistributedLockAdvisor distributedLockAdvisor(DistributedLockService distributedLockService) {
        return new DistributedLockAdvisor(distributedLockService);
    }
}

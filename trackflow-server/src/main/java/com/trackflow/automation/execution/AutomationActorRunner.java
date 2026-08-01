package com.trackflow.automation.execution;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 在持久化执行中恢复明确的业务操作人身份。
 *
 * <p>自动化可能由异步线程或服务重启后的调度器继续执行，不能依赖 HTTP 请求线程里的
 * SecurityContext。这里仅恢复用户 ID；真正的权限仍由 PermissionService 和工作流规则查询数据库判定。</p>
 */
@Component
public class AutomationActorRunner {
    private static final ThreadLocal<Boolean> AUTOMATION_EXECUTION =
            ThreadLocal.withInitial(() -> false);

    public static boolean isAutomationExecution() {
        return Boolean.TRUE.equals(AUTOMATION_EXECUTION.get());
    }

    public void runAs(Long actorUserId, Runnable action) {
        runAs(actorUserId, () -> {
            action.run();
            return null;
        });
    }

    public <T> T runAs(Long actorUserId, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        boolean previousAutomationState = AUTOMATION_EXECUTION.get();
        SecurityContext executionContext = SecurityContextHolder.createEmptyContext();
        if (actorUserId != null) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "automation:" + actorUserId, "N/A", List.of());
            authentication.setDetails(actorUserId);
            executionContext.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(executionContext);
        AUTOMATION_EXECUTION.set(true);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
            AUTOMATION_EXECUTION.set(previousAutomationState);
        }
    }
}

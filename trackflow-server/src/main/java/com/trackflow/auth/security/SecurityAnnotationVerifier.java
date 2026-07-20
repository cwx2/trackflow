package com.trackflow.auth.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 启动时扫描所有 Controller 端点，验证每个 /api/v1/ 端点必须有授权声明。
 * <p>
 * 授权声明包括：
 * <ul>
 *   <li>{@link PreAuthorize} — 方法级或类级</li>
 *   <li>{@link NoAuthorizationRequired} — 方法级或类级（显式豁免）</li>
 * </ul>
 * <p>
 * 参考 OpenProject {@code Accounts::Authorization} concern 的设计理念：
 * 如果一个端点既没有授权检查也没有显式豁免，说明开发者遗漏了——系统应该报警。
 * <p>
 * 行为：
 * <ul>
 *   <li>开发环境（默认）：打印 WARN 日志</li>
 *   <li>生产环境（可配置）：启动失败（fail-fast）</li>
 * </ul>
 *
 * @see NoAuthorizationRequired
 */
@Slf4j
@Component
public class SecurityAnnotationVerifier {

    private final ApplicationContext applicationContext;

    /**
     * 是否在发现未授权端点时阻断启动。
     * 默认 false（打印 WARN），生产环境建议设为 true。
     */
    @Value("${trackflow.security.enforce-authorization-check:false}")
    private boolean enforceOnStartup;

    public SecurityAnnotationVerifier(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void verify() {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);

        int totalEndpoints = 0;
        int protectedEndpoints = 0;
        List<String> unprotectedEndpoints = new ArrayList<>();

        for (Object controller : controllers.values()) {
            Class<?> clazz = getTargetClass(controller);
            String basePath = getBasePath(clazz);

            // 只检查 /api/v1/ 路径下的端点
            if (basePath == null || !basePath.startsWith("/api/v1")) {
                continue;
            }

            boolean classHasPreAuthorize = clazz.isAnnotationPresent(PreAuthorize.class);
            boolean classHasNoAuthRequired = clazz.isAnnotationPresent(NoAuthorizationRequired.class);

            for (Method method : clazz.getDeclaredMethods()) {
                if (!isEndpointMethod(method)) {
                    continue;
                }

                totalEndpoints++;
                String endpointPath = basePath + getMethodPath(method);
                String httpMethod = getHttpMethod(method);
                String endpoint = httpMethod + " " + endpointPath;

                if (classHasPreAuthorize || classHasNoAuthRequired
                        || method.isAnnotationPresent(PreAuthorize.class)
                        || method.isAnnotationPresent(NoAuthorizationRequired.class)) {
                    protectedEndpoints++;
                } else {
                    unprotectedEndpoints.add(endpoint + " (" + clazz.getSimpleName() + "#" + method.getName() + ")");
                }
            }
        }

        // 输出覆盖率统计
        double coverage = totalEndpoints > 0 ? (double) protectedEndpoints / totalEndpoints * 100 : 100;
        log.info("🔐 Security Annotation Coverage: {}/{} endpoints ({} %) have authorization declaration",
                protectedEndpoints, totalEndpoints, String.format("%.1f", coverage));

        if (unprotectedEndpoints.isEmpty()) {
            log.info("✅ All API endpoints have proper authorization annotations");
        } else {
            log.warn("⚠️ {} endpoint(s) missing @PreAuthorize or @NoAuthorizationRequired:", unprotectedEndpoints.size());
            for (String ep : unprotectedEndpoints) {
                log.warn("   - {}", ep);
            }

            if (enforceOnStartup) {
                throw new SecurityAnnotationException(
                        "Application startup blocked: " + unprotectedEndpoints.size()
                                + " endpoint(s) missing authorization annotation. "
                                + "Add @PreAuthorize or @NoAuthorizationRequired to each endpoint. "
                                + "Set trackflow.security.enforce-authorization-check=false to disable this check.");
            }
        }
    }

    /**
     * 获取目标类（处理 Spring CGLIB 代理）
     */
    private Class<?> getTargetClass(Object bean) {
        Class<?> clazz = bean.getClass();
        // CGLIB 代理的类名包含 $$
        if (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    /**
     * 获取 @RequestMapping 定义的基路径
     */
    private String getBasePath(Class<?> clazz) {
        RequestMapping rm = clazz.getAnnotation(RequestMapping.class);
        if (rm == null) return null;
        String[] paths = rm.value().length > 0 ? rm.value() : rm.path();
        return paths.length > 0 ? paths[0] : "";
    }

    /**
     * 判断方法是否是 HTTP 端点（有 @*Mapping 注解）
     */
    private boolean isEndpointMethod(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(RequestMapping.class);
    }

    /**
     * 获取方法级路径
     */
    private String getMethodPath(Method method) {
        String[] paths = null;
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping ann = method.getAnnotation(GetMapping.class);
            paths = ann.value().length > 0 ? ann.value() : ann.path();
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping ann = method.getAnnotation(PostMapping.class);
            paths = ann.value().length > 0 ? ann.value() : ann.path();
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping ann = method.getAnnotation(PutMapping.class);
            paths = ann.value().length > 0 ? ann.value() : ann.path();
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping ann = method.getAnnotation(DeleteMapping.class);
            paths = ann.value().length > 0 ? ann.value() : ann.path();
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            PatchMapping ann = method.getAnnotation(PatchMapping.class);
            paths = ann.value().length > 0 ? ann.value() : ann.path();
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping ann = method.getAnnotation(RequestMapping.class);
            paths = ann.value().length > 0 ? ann.value() : ann.path();
        }
        return (paths != null && paths.length > 0) ? paths[0] : "";
    }

    /**
     * 获取 HTTP 方法名
     */
    private String getHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class)) return "PATCH";
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping ann = method.getAnnotation(RequestMapping.class);
            return ann.method().length > 0 ? ann.method()[0].name() : "ANY";
        }
        return "UNKNOWN";
    }

    /**
     * 启动失败时抛出的异常
     */
    public static class SecurityAnnotationException extends RuntimeException {
        public SecurityAnnotationException(String message) {
            super(message);
        }
    }
}

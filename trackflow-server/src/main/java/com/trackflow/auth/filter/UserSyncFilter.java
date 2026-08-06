package com.trackflow.auth.filter;

import com.trackflow.auth.service.BackChannelLogoutService;
import com.trackflow.auth.service.UserSyncService;
import com.trackflow.common.util.WebUtils;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户同步过滤器：在 JWT 认证成功后同步用户信息到本地数据库，
 * 并将本地用户 ID 设置到 Authentication details 中。
 * <p>
 * 性能优化：使用本地缓存（syncCache）避免每次请求都执行 DB 同步。
 * 同一 keycloakId 在缓存有效期内（5 分钟）只同步一次，后续请求直接使用缓存的 userId。
 * <p>
 * 同时负责记录认证安全审计日志（login / first_login）。
 * 使用 JWT ID (jti) 去重，确保每个 token 只记录一次登录事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncFilter extends OncePerRequestFilter {

    private final UserSyncService userSyncService;
    private final SystemAuditService systemAuditService;
    private final StringRedisTemplate redisTemplate;

    /** Redis 黑名单 key 前缀（与 UserService 中保持一致） */
    private static final String DISABLED_USER_KEY_PREFIX = "auth:disabled:";

    /**
     * 用户同步结果缓存：避免每次请求都执行 DB 查询和 UPDATE。
     * key: keycloakId (jwt.subject), value: SyncCacheEntry (userId + 缓存时间)
     * <p>
     * 缓存命中（快速路径，99% 请求）：直接取 cachedUserId 设入 Authentication.details，跳过 syncFromJwt。
     * 缓存未命中或过期（慢速路径）：执行完整 syncFromJwt 并更新缓存。
     */
    private final ConcurrentHashMap<String, SyncCacheEntry> syncCache = new ConcurrentHashMap<>();

    /** 同步缓存 TTL：5 分钟（与 Keycloak JWT 有效期对齐） */
    private static final long SYNC_CACHE_TTL_MS = 5 * 60 * 1000L;

    /** 同步缓存最大条目数（防内存泄漏，超过后触发清理） */
    private static final int SYNC_CACHE_MAX_SIZE = 1000;

    /**
     * 已记录过登录事件的 JWT ID 缓存。
     * key: jti (JWT ID), value: 写入时间戳（用于定期清理过期条目）。
     * 由于 Keycloak token 有效期为 5 分钟，缓存条目在 10 分钟后自动清理。
     */
    private final ConcurrentHashMap<String, Long> loggedTokenIds = new ConcurrentHashMap<>();

    /** 登录事件缓存过期时间：10 分钟（毫秒） */
    private static final long LOGIN_CACHE_EXPIRY_MS = 10 * 60 * 1000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth && jwtAuth.isAuthenticated()) {
            try {
                Jwt jwt = jwtAuth.getToken();
                String keycloakId = jwt.getSubject();

                // 快速路径：缓存命中且未过期，跳过 DB 同步
                SyncCacheEntry cached = syncCache.get(keycloakId);
                Long userId;
                SysUser user = null;

                if (cached != null && !cached.isExpired()) {
                    // 缓存命中 — 但需检测 Keycloak 角色是否变更
                    userId = cached.userId();
                    Set<String> currentRoles = Set.copyOf(extractRealmRoles(jwt));
                    if (!currentRoles.equals(cached.cachedRoles())) {
                        // 角色变化：触发角色同步（仅角色，不重走完整 syncFromJwt）
                        userSyncService.syncKeycloakRolesOnly(userId, List.copyOf(currentRoles));
                        // 更新缓存中的角色快照
                        syncCache.put(keycloakId, new SyncCacheEntry(userId, cached.cachedAt(), currentRoles));
                        log.info("Keycloak roles changed for user {}, synced immediately: {} -> {}",
                                keycloakId, cached.cachedRoles(), currentRoles);
                    }
                } else {
                    // 缓存未命中或已过期 — 执行完整同步
                    user = userSyncService.syncFromJwt(jwt);
                    userId = user.getId();
                    Set<String> currentRoles = Set.copyOf(extractRealmRoles(jwt));
                    // 更新缓存
                    syncCache.put(keycloakId, new SyncCacheEntry(userId, System.currentTimeMillis(), currentRoles));
                    // 定期清理过期条目（概率触发）
                    cleanupSyncCacheIfNeeded();
                }

                // 将本地用户 ID 设置到 details 中，供后续 SecurityUtils 获取
                jwtAuth.setDetails(userId);

                // Redis 黑名单快速检查：用户被禁用后即时拦截（O(1)）
                if (isUserBlacklisted(userId)) {
                    // 禁用后移除缓存，确保下次走慢速路径
                    syncCache.remove(keycloakId);
                    String username = user != null ? user.getUsername() : keycloakId;
                    log.warn("Request blocked by Redis blacklist: userId={}, username={}",
                            userId, username);
                    logSessionBlockedById(userId, username, request, "user_disabled");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":40100,\"message\":\"User account has been disabled\",\"data\":null}"
                    );
                    return;
                }

                // Session 黑名单检查：用户已从 Keycloak 登出，session 已被 back-channel logout 失效
                if (isSessionLoggedOut(jwt)) {
                    syncCache.remove(keycloakId);
                    String username = user != null ? user.getUsername() : keycloakId;
                    log.warn("Request blocked by session logout blacklist: userId={}, username={}, sid={}",
                            userId, username, jwt.getClaimAsString("sid"));
                    logSessionBlockedById(userId, username, request, "session_logged_out");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":40100,\"message\":\"Session has been invalidated. Please login again.\",\"data\":null}"
                    );
                    return;
                }

                // 仅对"新颁发"的 token 记录登录事件（jti 去重 + 时间窗口）
                if (shouldLogLogin(jwt)) {
                    // 登录审计需要完整 user 对象；若走缓存路径未加载，需查一次
                    if (user == null) {
                        user = userSyncService.syncFromJwt(jwt);
                    }
                    logLoginEvent(user, jwt, request);
                }
            } catch (Exception e) {
                log.error("User sync failed: {}", e.getMessage());
                // 如果是 BusinessException（如用户被禁用），向上抛出
                if (e instanceof com.trackflow.common.exception.BusinessException) {
                    // 记录登录失败事件（用户被禁用）
                    logLoginFailedFromJwt(request, "user_disabled");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":40100,\"message\":\"User account has been disabled\",\"data\":null}"
                    );
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 同步缓存条目：存储用户 ID、缓存创建时间和缓存时的 Keycloak realm 角色。
     * <p>
     * cachedRoles 用于快速路径中检测角色变更：
     * 若 JWT 中的 realm roles 与缓存不同，触发 syncKeycloakRolesToLocal，确保角色变更即时生效。
     */
    private record SyncCacheEntry(Long userId, long cachedAt, Set<String> cachedRoles) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > SYNC_CACHE_TTL_MS;
        }
    }

    /**
     * 主动失效指定用户的同步缓存。
     * 在用户被禁用、角色变更等场景下由外部调用，确保下次请求走慢速路径重新同步。
     */
    public void invalidateSyncCache(String keycloakId) {
        if (keycloakId != null) {
            syncCache.remove(keycloakId);
        }
    }

    /**
     * 从 JWT 中提取 Keycloak realm_access.roles 列表。
     * 用于快速路径中比较角色变化，不依赖 UserSyncService（避免注入完整同步流程）。
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    /**
     * 清理过期的同步缓存条目。
     * 仅在缓存大小超过阈值时触发（概率清理），避免每次请求都遍历。
     */
    private void cleanupSyncCacheIfNeeded() {
        if (syncCache.size() > SYNC_CACHE_MAX_SIZE) {
            long cutoff = System.currentTimeMillis() - SYNC_CACHE_TTL_MS;
            Iterator<Map.Entry<String, SyncCacheEntry>> iterator = syncCache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, SyncCacheEntry> entry = iterator.next();
                if (entry.getValue().cachedAt() < cutoff) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 检查用户是否在 Redis 黑名单中（被禁用后的即时拦截）。
     * Redis key 存在 = 用户被禁用，O(1) 快速检查。
     */
    private boolean isUserBlacklisted(Long userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(DISABLED_USER_KEY_PREFIX + userId));
        } catch (Exception e) {
            // Redis 不可用时 fallback 到 DB 检查（UserSyncService 已处理）
            log.warn("Redis blacklist check failed, falling back to DB: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 JWT 所属的 session 是否已通过 back-channel logout 被注销。
     * <p>
     * 优先检查 sid (session ID) — 精确匹配单个 session。
     * 如果 JWT 不含 sid claim，检查 sub (keycloak_id) 的 user-level 黑名单。
     * <p>
     * Redis 不可用时降级放行（与 isUserBlacklisted 策略一致）。
     */
    private boolean isSessionLoggedOut(Jwt jwt) {
        try {
            // 优先：按 session ID 检查
            String sessionId = jwt.getClaimAsString("sid");
            if (sessionId != null && !sessionId.isBlank()) {
                return Boolean.TRUE.equals(redisTemplate.hasKey(
                        BackChannelLogoutService.LOGOUT_SESSION_KEY_PREFIX + sessionId));
            }

            // 兜底：按 keycloak_id (sub) 检查 user-level 黑名单
            String keycloakId = jwt.getSubject();
            if (keycloakId != null && !keycloakId.isBlank()) {
                return Boolean.TRUE.equals(redisTemplate.hasKey("auth:logout:user:" + keycloakId));
            }

            return false;
        } catch (Exception e) {
            // Redis 不可用时降级放行，退化为 JWT 自然过期
            log.warn("Session logout blacklist check failed, falling back to JWT expiry: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断是否应为此 JWT 记录登录事件。
     * 条件：
     * 1. token 是新颁发的（issued_at 在 120 秒内）
     * 2. 此 token 的 jti 尚未被记录过（避免同一 token 多次请求重复记录）
     */
    private boolean shouldLogLogin(Jwt jwt) {
        if (jwt.getIssuedAt() == null) return false;

        // 条件1: token 必须是新颁发的
        long issuedAtSeconds = jwt.getIssuedAt().getEpochSecond();
        long nowSeconds = java.time.Instant.now().getEpochSecond();
        if ((nowSeconds - issuedAtSeconds) > 120) return false;

        // 条件2: jti 去重
        String jti = jwt.getId();
        if (jti == null) {
            // 没有 jti 的 token 仅靠时间窗口判断（fallback）
            return true;
        }

        // 定期清理过期条目（简单概率清理，避免内存泄漏）
        if (loggedTokenIds.size() > 100) {
            long cutoff = System.currentTimeMillis() - LOGIN_CACHE_EXPIRY_MS;
            loggedTokenIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }

        // putIfAbsent 返回 null 表示首次插入
        return loggedTokenIds.putIfAbsent(jti, System.currentTimeMillis()) == null;
    }

    /**
     * 记录 JWT 登录成功审计事件。
     * 仅在新 token 首次使用时调用（由 shouldLogLogin 判断）。
     */
    private void logLoginEvent(SysUser user, Jwt jwt, HttpServletRequest request) {
        try {
            // 判断是否为首次登录：createdAt 和 lastLoginAt 差值极小（同一事务内设置）
            boolean isFirstLogin = user.getCreatedAt() != null
                    && user.getLastLoginAt() != null
                    && java.time.Duration.between(user.getCreatedAt(), user.getLastLoginAt()).abs().getSeconds() <= 2;

            String action = isFirstLogin ? "first_login" : "login";

            systemAuditService.logAuthEvent(
                    action,
                    user.getId(),
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    Map.of("method", "jwt", "username", user.getUsername())
            );
        } catch (Exception e) {
            // 审计日志失败不阻塞正常请求
            log.warn("Failed to log auth event for user {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * 记录 JWT 登录失败事件（如用户被禁用）
     */
    private void logLoginFailedFromJwt(HttpServletRequest request, String reason) {
        try {
            systemAuditService.logAuthEvent(
                    "login_failed",
                    null,
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    Map.of("method", "jwt", "reason", reason)
            );
        } catch (Exception e) {
            log.warn("Failed to log auth failure event: {}", e.getMessage());
        }
    }

    /**
     * 记录会话被阻断事件（通过 userId 和 username）。
     * 在缓存路径中可能没有完整 SysUser 对象，因此接受原始字段。
     */
    private void logSessionBlockedById(Long userId, String username, HttpServletRequest request, String reason) {
        try {
            systemAuditService.logAuthEvent(
                    "session_blocked",
                    userId,
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    Map.of("method", "jwt", "reason", reason, "username", username)
            );
        } catch (Exception e) {
            log.warn("Failed to log session_blocked event: {}", e.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 跳过不需要认证的路径
        String path = request.getRequestURI();
        return path.startsWith("/doc.html")
                || path.startsWith("/webjars/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator/");
    }
}

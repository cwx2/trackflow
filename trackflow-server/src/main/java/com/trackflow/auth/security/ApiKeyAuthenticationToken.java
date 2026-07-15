package com.trackflow.auth.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * API Key 认证 Token，携带 scope 信息用于权限交叉过滤。
 * <p>
 * 当 scope 为空集合时表示不限制（向后兼容）；
 * 非空时实际权限 = 用户角色权限 ∩ apiKeyScope。
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String username;
    private final Long userId;
    /**
     * -- GETTER --
     *  获取 API Key 的权限范围。
     *  空集合表示不限制（全量权限）；非空表示只允许这些权限。
     */
    @Getter
    private final Set<String> scope;

    public ApiKeyAuthenticationToken(String username, Long userId, Set<String> scope,
                                     Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.username = username;
        this.userId = userId;
        this.scope = scope != null ? Set.copyOf(scope) : Collections.emptySet();
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public Object getDetails() {
        return userId;
    }

    /**
     * 判断是否有 scope 限制
     */
    public boolean hasScopeRestriction() {
        return !scope.isEmpty();
    }
}

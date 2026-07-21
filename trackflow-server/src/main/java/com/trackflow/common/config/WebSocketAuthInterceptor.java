package com.trackflow.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

/**
 * WebSocket STOMP 认证拦截器。
 * <p>
 * 在 CONNECT 帧中验证 JWT token（通过 Authorization header 或 passcode 传递），
 * 设置 Principal 用于后续消息路由和权限校验。
 */
@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final SysUserMapper sysUserMapper;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 从 STOMP header 中提取 token
                    String token = extractToken(accessor);
                    if (token == null || token.isBlank()) {
                        log.warn("[WebSocket] CONNECT 无 token，拒绝连接");
                        throw new IllegalArgumentException("Missing authentication token");
                    }

                    try {
                        Jwt jwt = jwtDecoder.decode(token);
                        String keycloakId = jwt.getSubject();

                        // 查找本地用户
                        SysUser user = sysUserMapper.selectOne(
                                new LambdaQueryWrapper<SysUser>()
                                        .eq(SysUser::getKeycloakId, keycloakId)
                        );
                        if (user == null) {
                            log.warn("[WebSocket] 用户未同步: keycloakId={}", keycloakId);
                            throw new IllegalArgumentException("User not found");
                        }

                        // 设置 Principal，后续可用于消息路由
                        Principal principal = new UsernamePasswordAuthenticationToken(
                                user.getId().toString(),
                                null,
                                Collections.emptyList()
                        );
                        accessor.setUser(principal);

                        log.debug("[WebSocket] 用户 {} ({}) 连接成功", user.getDisplayName(), user.getId());
                    } catch (Exception e) {
                        log.warn("[WebSocket] JWT 验证失败: {}", e.getMessage());
                        throw new IllegalArgumentException("Invalid token");
                    }
                }

                return message;
            }
        });
    }

    /**
     * 从 STOMP CONNECT 帧中提取 JWT token。
     * 支持两种方式：
     * 1. Authorization header: "Bearer xxx"
     * 2. token header（前端 @stomp/stompjs 使用 connectHeaders）
     */
    private String extractToken(StompHeaderAccessor accessor) {
        // 方式1: Authorization header
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String auth = authHeaders.get(0);
            if (auth.startsWith("Bearer ")) {
                return auth.substring(7);
            }
            return auth;
        }

        // 方式2: token header（前端 @stomp/stompjs 使用 connectHeaders）
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // 方式3: passcode（标准 STOMP 登录字段）
        String passcode = accessor.getPasscode();
        return passcode;
    }
}

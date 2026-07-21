package com.trackflow.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置 — 支持实时推送 Issue 变更事件到前端。
 * <p>
 * 使用内存消息代理（SimpleBroker），适合单节点部署。
 * 若后续需要多实例水平扩展，可替换为 Redis/RabbitMQ 外部代理。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户端订阅的目标前缀（服务端推送到这些 topic）
        config.enableSimpleBroker("/topic");
        // 客户端发送消息到服务端的前缀（本场景不需要客户端→服务端消息，但保留标准配置）
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，允许跨域（开发环境前端 localhost:3000）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}

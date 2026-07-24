package com.trackflow.common.config;

import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * CORS 限制与 REST API 使用同一配置源（trackflow.cors.allowed-origins）。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${trackflow.cors.allowed-origins}") String allowedOriginsStr) {
        this.allowedOrigins = allowedOriginsStr.split(",");
        // Trim each origin
        for (int i = 0; i < this.allowedOrigins.length; i++) {
            this.allowedOrigins[i] = this.allowedOrigins[i].trim();
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户端订阅的目标前缀：
        // - /topic: 广播类（项目级 Issue 变更）
        // - /queue: 用户级（通知推送，通过 /user/{userId}/queue/notifications 路由）
        config.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息到服务端的前缀（本场景不需要客户端→服务端消息，但保留标准配置）
        config.setApplicationDestinationPrefixes("/app");
        // 用户目的地前缀 — 支持 convertAndSendToUser() 按 principal 路由到指定用户
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，使用与 REST API 相同的 CORS 白名单
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
    }
}

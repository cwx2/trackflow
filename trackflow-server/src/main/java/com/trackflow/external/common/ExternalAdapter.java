package com.trackflow.external.common;

import java.util.Set;

/**
 * 第三方集成适配器接口（SPI）。
 * <p>
 * 每个外部系统对接（Email、SUG、Webhook 等）都需要实现此接口。
 * 通过 Spring 注入 + {@link ExternalEventPublisher} 自动发现所有适配器。
 * <p>
 * 设计参考：OpenProject 的 Webhooks::Webhook 模型 + ActiveJob 分发模式。
 */
public interface ExternalAdapter {

    /**
     * 适配器唯一标识（如 "email", "sug", "webhook"）
     */
    String getAdapterType();

    /**
     * 适配器显示名称
     */
    String getDisplayName();

    /**
     * 该适配器关注的事件类型集合。
     * 只有匹配的事件才会被分发到此适配器。
     */
    Set<ExternalEventType> getSupportedEvents();

    /**
     * 处理出站事件。
     * <p>
     * 实现者应当：
     * 1. 根据事件类型和载荷构造外部请求
     * 2. 发送请求到外部系统
     * 3. 返回处理结果
     * <p>
     * 异常处理：如果执行失败，抛出 RuntimeException，
     * 由 {@link ExternalEventPublisher} 统一记录到 external_event_log 并安排重试。
     *
     * @param event 要处理的出站事件
     */
    void handle(ExternalEvent event);

    /**
     * 适配器是否启用。
     * 可根据配置动态关闭某个适配器。
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 最大重试次数。默认 3 次。
     */
    default int getMaxRetries() {
        return 3;
    }

    /**
     * 重试间隔（秒）。默认 60 秒指数退避。
     */
    default int getRetryIntervalSeconds() {
        return 60;
    }
}

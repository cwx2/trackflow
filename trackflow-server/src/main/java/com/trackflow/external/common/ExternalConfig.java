package com.trackflow.external.common;

/**
 * 第三方集成配置基类。
 * <p>
 * 各适配器的配置（如邮件服务器地址、SUG API Key、Webhook URL 等）
 * 可继承此类并通过 @ConfigurationProperties 绑定。
 */
public abstract class ExternalConfig {

    /** 是否启用此集成 */
    private boolean enabled = false;

    /** 超时时间（毫秒） */
    private int timeoutMs = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

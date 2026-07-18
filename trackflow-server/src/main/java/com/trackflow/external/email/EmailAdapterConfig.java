package com.trackflow.external.email;

import com.trackflow.external.common.ExternalConfig;

/**
 * 邮件集成配置。
 * <p>
 * 后续实现将通过 @ConfigurationProperties("trackflow.external.email") 绑定。
 */
public class EmailAdapterConfig extends ExternalConfig {

    /** SMTP 服务器地址 */
    private String smtpHost;

    /** SMTP 端口 */
    private int smtpPort = 587;

    /** 发送人地址 */
    private String fromAddress;

    /** 是否开启 TLS */
    private boolean useTls = true;

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }
}

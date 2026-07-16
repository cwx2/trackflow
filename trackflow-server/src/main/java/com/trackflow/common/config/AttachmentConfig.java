package com.trackflow.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * 附件上传配置
 * <p>
 * 通过 application.yml 中 trackflow.attachment.* 配置项管理。
 * 包含文件大小限制、数量限制、类型黑名单。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "trackflow.attachment")
public class AttachmentConfig {

    /**
     * 单文件最大大小（字节），默认 50MB
     */
    private long maxFileSize = 50L * 1024 * 1024;

    /**
     * 单工单最大附件数量
     */
    private int maxAttachmentsPerIssue = 50;

    /**
     * 禁止上传的文件扩展名（小写，不含点）
     */
    private Set<String> blockedExtensions = new HashSet<>(Set.of(
            "exe", "bat", "cmd", "sh", "ps1", "vbs", "js", "msi",
            "dll", "com", "scr", "pif", "hta", "cpl", "inf", "reg",
            "ws", "wsf", "wsc", "lnk"
    ));

    /**
     * 获取人类可读的最大文件大小描述
     */
    public String getMaxFileSizeReadable() {
        long mb = maxFileSize / (1024 * 1024);
        if (mb > 0) return mb + "MB";
        long kb = maxFileSize / 1024;
        return kb + "KB";
    }
}

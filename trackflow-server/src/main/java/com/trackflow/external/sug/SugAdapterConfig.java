package com.trackflow.external.sug;

import com.trackflow.external.common.ExternalConfig;

/**
 * SUG 系统集成配置。
 * <p>
 * 后续实现将通过 @ConfigurationProperties("trackflow.external.sug") 绑定。
 */
public class SugAdapterConfig extends ExternalConfig {

    /** SUG API 基础地址 */
    private String apiBaseUrl;

    /** SUG API Key */
    private String apiKey;

    /** 项目映射 ID（TrackFlow 项目 → SUG 项目） */
    private String projectMapping;

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getProjectMapping() {
        return projectMapping;
    }

    public void setProjectMapping(String projectMapping) {
        this.projectMapping = projectMapping;
    }
}

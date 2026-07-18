package com.trackflow.external.migration;

import com.trackflow.external.common.ExternalConfig;

/**
 * 数据迁移配置。
 * <p>
 * 后续实现将通过 @ConfigurationProperties("trackflow.external.migration") 绑定。
 */
public class MigrationAdapterConfig extends ExternalConfig {

    /** 源系统类型（如 "youtrack", "jira"） */
    private String sourceType;

    /** 源数据文件路径或 API 地址 */
    private String sourceUrl;

    /** 是否进行 dry-run 模式（只校验不导入） */
    private boolean dryRun = true;

    /** 批量导入大小 */
    private int batchSize = 100;

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}

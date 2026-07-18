package com.trackflow.external.vo;

import lombok.Data;

import java.util.Map;

/**
 * 第三方集成配置 VO
 */
@Data
public class IntegrationConfigVO {

    /** 适配器类型 */
    private String adapterType;

    /** 适配器显示名称 */
    private String displayName;

    /** 是否启用 */
    private boolean enabled;

    /** 配置键值对（key 去掉 external.{adapterType}. 前缀后的短 key → value） */
    private Map<String, String> config;
}

package com.trackflow.customfield.vo;

import lombok.Data;

import java.util.List;

/**
 * 可用的类型转换选项 VO
 */
@Data
public class AvailableConversionsVO {

    /**
     * 当前字段类型
     */
    private String currentFormat;

    /**
     * 可转换的目标类型列表
     */
    private List<ConversionOption> availableTargets;

    /**
     * 是否允许类型转换（如字段被看板使用则不允许）
     */
    private boolean conversionAllowed;

    /**
     * 不允许转换的原因（当 conversionAllowed=false 时）
     */
    private String blockedReason;

    @Data
    public static class ConversionOption {
        /**
         * 目标类型代码
         */
        private String format;

        /**
         * 目标类型显示名称
         */
        private String displayName;

        /**
         * 是否需要额外选项（如数值转 period 需要指定单位）
         */
        private boolean requiresOptions;

        /**
         * 可用的转换选项（如 MINUTES/HOURS/DAYS）
         */
        private List<String> options;

        /**
         * 转换警告信息（如可能丢失数据）
         */
        private String warning;
    }
}

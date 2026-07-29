package com.trackflow.customfield.vo;

import lombok.Data;

/**
 * 类型转换结果 VO
 */
@Data
public class ConversionResultVO {

    /**
     * 字段 ID
     */
    private String fieldId;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 原类型
     */
    private String fromFormat;

    /**
     * 新类型
     */
    private String toFormat;

    /**
     * 受影响的 issue 数量
     */
    private int affectedIssueCount;

    /**
     * 成功转换的值数量
     */
    private int convertedValueCount;

    /**
     * 转换失败的值数量（数据不兼容）
     */
    private int failedValueCount;

    /**
     * 警告信息（如部分数据丢失）
     */
    private String warning;
}

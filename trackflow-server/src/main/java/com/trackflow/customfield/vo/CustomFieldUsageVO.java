package com.trackflow.customfield.vo;

import lombok.Data;

/**
 * 自定义字段使用情况统计
 */
@Data
public class CustomFieldUsageVO {
    /** 有值记录的工单数量 */
    private long issueCount;
    /** 字段值记录总数 */
    private long valueCount;
    /** 关联的项目数量（全局字段=系统中所有项目数） */
    private long projectCount;
    /** 是否为全局字段 */
    private Boolean isForAll;
    /** 关联的 Issue 类型数量 */
    private long issueTypeCount;
    /** 选项数量（仅 list 类型） */
    private long optionCount;
    /** 被其他字段作为条件源引用的数量 */
    private long conditionRefCount;
}

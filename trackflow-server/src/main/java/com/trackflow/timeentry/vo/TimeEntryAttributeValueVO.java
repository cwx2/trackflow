package com.trackflow.timeentry.vo;

import lombok.Data;

/**
 * 工时记录属性值 VO
 * 对应工作项属性值（如 Work Type: Development / Testing）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class TimeEntryAttributeValueVO {
    /** 属性定义 ID */
    private String attributeId;
    /** 属性名称（如 "Work type"） */
    private String attributeName;
    /** 属性值 ID */
    private String valueId;
    /** 属性值名称（如 "Development"） */
    private String valueName;
    /** 属性值颜色（十六进制，如 "#4caf50"） */
    private String valueColor;
}

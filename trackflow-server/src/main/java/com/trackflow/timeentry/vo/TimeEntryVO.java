package com.trackflow.timeentry.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TimeEntryVO {
    private String id;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private String userId;
    private String userName;
    private String workDate;
    private Integer duration;       // minutes
    private Integer startTime;      // minutes from midnight
    private String description;
    private String createdAt;
    private String updatedAt;

    /**
     * 工作类型名称（从 time_entry_attribute_value 解析得出）
     * 用于向下兼容展示，前端筛选应使用 activityId
     */
    private String workType;

    /**
     * 工作类型属性值 ID（用于筛选）
     */
    private String workTypeId;

    /**
     * 工作类型颜色
     */
    private String workTypeColor;

    /**
     * 工作项属性值列表
     * 每项包含: attributeId, attributeName, valueId, valueName, valueColor
     */
    private List<Map<String, String>> attributeValues;
}

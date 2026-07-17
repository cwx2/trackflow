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
    private String workType;
    private String description;
    private String createdAt;
    private String updatedAt;

    /**
     * 工作项属性值列表
     * 每项包含: attributeId, attributeName, valueId, valueName, valueColor
     */
    private List<Map<String, String>> attributeValues;
}

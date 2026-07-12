package com.trackflow.timeentry.vo;

import lombok.Data;

@Data
public class TimeEntryVO {
    private String id;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private String userId;
    private String workDate;
    private Integer duration;       // minutes
    private Integer startTime;      // minutes from midnight
    private String workType;
    private String description;
    private String createdAt;
    private String updatedAt;
}

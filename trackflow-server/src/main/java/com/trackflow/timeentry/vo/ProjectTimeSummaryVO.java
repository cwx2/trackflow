package com.trackflow.timeentry.vo;

import lombok.Data;

import java.util.List;

/**
 * 项目工时汇总 VO - 用于时间表"项目"视图
 */
@Data
public class ProjectTimeSummaryVO {
    private String projectId;
    private String projectName;
    private String projectKey;
    private Integer totalDuration;  // minutes
    private List<TimeEntryVO> entries;
}

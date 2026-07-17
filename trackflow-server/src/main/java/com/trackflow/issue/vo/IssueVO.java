package com.trackflow.issue.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class IssueVO {
    private String id;
    private String projectId;
    private String issueKey;
    private String title;
    private String issueType;
    private String statusId;
    private String priority;
    private String assigneeId;
    private String assigneeName;
    private String assigneeAvatarUrl;
    private String reporterId;
    private String reporterName;
    private String sprintId;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    /** 直接子工单总数 */
    private Integer childCount;
    /** 已关闭的直接子工单数 */
    private Integer childClosedCount;

    /** 自定义字段值，key 格式为 "cf_{fieldId}"，value 为展示用文本 */
    private Map<String, String> customFieldValues;

    /** 自定义字段颜色，key 格式为 "cf_{fieldId}"，value 为 HEX 颜色值（仅 list 类型且配置了颜色时存在） */
    private Map<String, String> customFieldColors;
}

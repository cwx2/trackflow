package com.trackflow.issue.vo;

import com.trackflow.customfield.vo.CustomFieldValueVO;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class IssueVO {
    private String id;
    private String projectId;
    private String issueKey;
    private String title;
    private String issueType;
    private String statusId;
    private String statusName;
    private String statusColor;
    private String priority;
    private String assigneeId;
    private String assigneeName;
    private String assigneeAvatarUrl;
    private String reporterId;
    private String reporterName;
    private String sprintId;
    private String sprintName;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private Integer version;

    /** 预估工时 */
    private BigDecimal estimatedHours;

    /** 直接子工单总数 */
    private Integer childCount;
    /** 已关闭的直接子工单数 */
    private Integer childClosedCount;

    /**
     * 自定义字段结构化详情，每个字段独立表达 value/values、displayValue/displayValues、color/colors。
     * 前端应优先使用此字段渲染多值标签。
     */
    private List<CustomFieldValueVO> customFieldDetails;

    /**
     * @deprecated 使用 {@link #customFieldDetails} 代替。保留仅做向后兼容，下个版本移除。
     */
    @Deprecated
    private Map<String, String> customFieldValues;

    /**
     * @deprecated 使用 {@link #customFieldDetails} 代替。保留仅做向后兼容，下个版本移除。
     */
    @Deprecated
    private Map<String, String> customFieldColors;
}

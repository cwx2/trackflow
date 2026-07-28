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

    /**
     * 工单关联的所有 Sprint ID 列表（多 Sprint 模式时非空）。
     * 包含主 Sprint 及追加的 Sprint。
     */
    private List<String> sprintIds;

    /**
     * 工单关联的所有 Sprint 名称列表（与 sprintIds 顺序对应）。
     */
    private List<String> sprintNames;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private Integer version;

    /** 预估工时 */
    private BigDecimal estimatedHours;
    /** 已花时间 */
    private BigDecimal spentHours;
    /** 派生字段：自身 + 所有后代 spent_hours 总和 */
    private BigDecimal derivedSpentHours;
    /** 派生字段：自身 + 所有后代 estimated_hours 总和 */
    private BigDecimal derivedEstimatedHours;

    /** 直接子工单总数 */
    private Integer childCount;
    /** 已关闭的直接子工单数 */
    private Integer childClosedCount;

    /** 投票数冗余字段（来自 issue.vote_count） */
    private Integer voteCount;

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

    /**
     * 工单可见性：public（项目所有成员可见）或 restricted（仅限指定用户）。
     * 默认 public。
     */
    private String visibility;
}

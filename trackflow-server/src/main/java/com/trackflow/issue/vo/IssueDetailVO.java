package com.trackflow.issue.vo;

import com.trackflow.customfield.vo.CustomFieldValueVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class IssueDetailVO {
    private String id;
    private String projectId;
    private String projectName;
    private String projectStatus;
    private String issueKey;
    private String title;
    private String description;
    private String issueType;
    private String statusId;
    private IssueStatusVO status;
    private String priority;
    /** 优先级颜色（HEX 格式，来自 custom_field_option.color） */
    private String priorityColor;
    /** 工单类型颜色（HEX 格式，来自 custom_field_option.color） */
    private String issueTypeColor;
    private String assigneeId;
    private String assigneeName;
    private String assigneeAvatarUrl;
    private String reporterId;
    private String reporterName;
    private String sprintId;
    private String sprintName;
    /** Sprint 状态（planned/active/completed/archived），用于前端视觉区分已完成迭代 */
    private String sprintStatus;

    /**
     * 工单关联的所有 Sprint ID 列表（多 Sprint 模式时非空）。
     * 包含主 Sprint 及追加的 Sprint。
     */
    private List<String> sprintIds;

    /**
     * 工单关联的所有 Sprint 名称列表（与 sprintIds 顺序对应）。
     */
    private List<String> sprintNames;
    private String parentId;
    private String parentKey;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private BigDecimal spentHours;
    /** 派生字段：自身 + 所有后代的 spent_hours 总和 */
    private BigDecimal derivedSpentHours;
    /** 派生字段：自身 + 所有后代的 estimated_hours 总和 */
    private BigDecimal derivedEstimatedHours;
    /** 结构化自定义字段值列表（EAV 表数据，用于前端渲染） */
    private List<CustomFieldValueVO> customFieldDetails;
    private List<IssueTagVO> tags;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    // 创建者/更新者信息
    private String createdById;
    private String createdByName;
    private String updatedById;
    private String updatedByName;

    /** 投票数冗余字段（来自 issue.vote_count） */
    private Integer voteCount;

    /** 子任务列表（仅当本工单有子任务时非空） */
    private List<ChildIssueVO> children;
    /** 子任务进度汇总（仅当有子任务时非空） */
    private ChildProgressVO childProgress;

    /** 类型变更导致状态自动重置时为 true（仅 update 响应中出现） */
    private Boolean statusAutoReset;

    /**
     * 工单可见性：public（项目所有成员可见）或 restricted（仅限指定用户）。
     * 默认 public。
     */
    private String visibility;

    /**
     * 受限工单的可见用户 ID 列表（仅 visibility=restricted 时非空）。
     * 格式：String ID 列表（避免 JS 精度丢失）。
     */
    private List<String> visibilityUserIds;

    /**
     * 受限工单的可见用户显示名列表（与 visibilityUserIds 顺序对应）。
     */
    private List<String> visibilityUserNames;
}

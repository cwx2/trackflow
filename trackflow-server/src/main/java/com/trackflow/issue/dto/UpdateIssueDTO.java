package com.trackflow.issue.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class UpdateIssueDTO {

    @Size(max = 500, message = "标题不能超过500字符")
    private String title;

    private String description;
    private String issueType;
    private String priority;
    private Long assigneeId;
    private Long sprintId;
    private Long parentId;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Map<String, String> customFields;

    /**
     * 清空截止日期标志。为 true 时将 due_date 设为 NULL，
     * 用于解决 JSON null 无法区分"未传"和"清空"的语义歧义。
     */
    private Boolean clearDueDate;

    /**
     * 清空预估工时标志。为 true 时将 estimated_hours 设为 NULL。
     */
    private Boolean clearEstimatedHours;

    /**
     * 乐观锁版本号。前端从 GET 响应中获取，PUT 时携带回来。
     * 为 null 时兼容旧客户端（跳过版本校验）。
     */
    private Integer version;

    /**
     * 强制跳过 WIP 上限检查（用户在前端确认弹窗后再次提交时设为 true）。
     * 仅在更新 priority 字段且看板为优先级模式时生效。
     */
    private Boolean forceWip;

    /**
     * 追加 Sprint ID（多 Sprint 模式）。
     * 与 sprintId 互斥：sprintId 用于覆盖主 Sprint，addToSprintId 用于追加关联。
     * 需要项目看板配置 allowMultipleSprints=true，否则返回警告。
     */
    private Long addToSprintId;

    /**
     * 从工单移除指定 Sprint 关联（多 Sprint 模式）。
     * 仅从 issue_sprint 关联表中移除，不影响主 sprint_id。
     */
    private Long removeFromSprintId;

    /**
     * 工单可见性：public（项目所有成员可见）或 restricted（仅限指定用户）。
     * 传入该字段时必须同时传 visibilityUserIds（restricted 时指定可见用户）。
     */
    private String visibility;

    /**
     * 受限工单的可见用户 ID 列表（仅 visibility=restricted 时有效）。
     * 传 null 或空列表表示清空（退化为仅报告者可见）。
     */
    private List<Long> visibilityUserIds;
}

package com.trackflow.issue.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}

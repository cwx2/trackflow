package com.trackflow.sprint.mapper.result;

import com.trackflow.sprint.vo.StatusBreakdownItem;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 列表查询的强类型结果行（含工单统计、项目信息）。
 * <p>
 * 由 MyBatis XML resultMap 直接映射（Long→String ID 转换在 XML 中处理）。
 * Service 层返回此类型，Controller 层转为 SprintVO 返回给前端。
 * <p>
 * 字段与 SprintVO 一一对应，但语义上属于「数据库查询结果」而非「API 响应对象」。
 */
@Data
public class SprintStatsRow {
    private String id;
    private String projectId;
    private String projectName;
    private String projectKey;
    private String name;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;

    // ===== 状态推导（Service 层填充） =====
    private String statusHint;
    private boolean overdue;

    // ===== Issue 统计（SQL JOIN 查询填充） =====
    private int totalIssues;
    private int doneIssues;
    private int inProgressIssues;
    private int todoIssues;
    private int overdueIssues;
    private int unassignedIssues;

    // ===== 激活快照 =====
    private LocalDateTime startedAt;
    private Double startScopeHours;
    private Integer startScopeIssues;

    // ===== 工时统计 =====
    private double totalEstimatedHours;
    private double completedEstimatedHours;

    // ===== 按状态细分统计（Service 层填充） =====
    private List<StatusBreakdownItem> statusBreakdown;
}

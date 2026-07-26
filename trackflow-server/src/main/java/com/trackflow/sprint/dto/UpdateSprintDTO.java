package com.trackflow.sprint.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Sprint 更新请求 DTO
 * 所有字段可选（null 表示不修改该字段）
 */
@Data
public class UpdateSprintDTO {

    @Size(max = 200, message = "Sprint名称不能超过200字符")
    private String name;

    @Size(max = 2000, message = "Sprint目标不能超过2000字符")
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * 是否确认日期重叠。
     * 当后端检测到日期与已有 Sprint 重叠时，会返回 40901 错误码和重叠信息。
     * 前端展示确认弹窗后，带 confirmOverlap=true 重新提交以跳过重叠检测。
     */
    private Boolean confirmOverlap;

    /**
     * 是否清空开始日期（true = 将 start_date 设为 NULL，恢复为"未排期"状态）。
     * 与 startDate 互斥：clearStartDate=true 时忽略 startDate 的值。
     * 对标 YouTrack：Sprint 日期是可选的，用户可随时将日期清空（unscheduled sprint）。
     */
    private Boolean clearStartDate;

    /**
     * 是否清空结束日期（true = 将 end_date 设为 NULL）。
     * 与 endDate 互斥：clearEndDate=true 时忽略 endDate 的值。
     */
    private Boolean clearEndDate;
}

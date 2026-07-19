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
}

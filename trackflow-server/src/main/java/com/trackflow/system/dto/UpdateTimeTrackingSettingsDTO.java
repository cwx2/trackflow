package com.trackflow.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新时间追踪设置 DTO
 */
@Data
public class UpdateTimeTrackingSettingsDTO {

    /** 每日工作小时数（1-24） */
    @NotNull(message = "每日工作小时数不能为空")
    @Min(value = 1, message = "每日工作小时数不能小于1")
    @Max(value = 24, message = "每日工作小时数不能大于24")
    private Integer hoursPerDay;

    /** 每周工作日列表（1=周一, 7=周日） */
    @NotEmpty(message = "工作日不能为空")
    private List<Integer> workingDays;
}

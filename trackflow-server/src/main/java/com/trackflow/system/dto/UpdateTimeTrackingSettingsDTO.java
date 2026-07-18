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

    /**
     * 工时重新计算策略（仅当 hoursPerDay 变更时需要）。
     * <ul>
     *   <li>PRESERVE_MINUTES — 保留分钟值不变，仅更新天/小时的换算展示</li>
     *   <li>PRESERVE_DAYS — 按比例重新计算分钟值，使天数展示保持不变</li>
     *   <li>null — hoursPerDay 未变更时无需传递</li>
     * </ul>
     */
    private RecalculationStrategy recalculationStrategy;

    /**
     * 工时重新计算策略枚举
     */
    public enum RecalculationStrategy {
        /** 保留分钟值：time_entry.duration 不变，仅前端展示更新 */
        PRESERVE_MINUTES,
        /** 保留天数：time_entry.duration 按比例重新计算 */
        PRESERVE_DAYS
    }
}

package com.trackflow.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 回收站保留策略更新 DTO
 * trashRetentionDays = 0 表示永久保留，不自动清理
 */
@Data
public class UpdateTrashSettingsDTO {

    @NotNull(message = "保留天数不能为空")
    @Min(value = 0, message = "保留天数不能为负数")
    @Max(value = 365, message = "保留天数不能超过365天")
    private Integer trashRetentionDays;
}

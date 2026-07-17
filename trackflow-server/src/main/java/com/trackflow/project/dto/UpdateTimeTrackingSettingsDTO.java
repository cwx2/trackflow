package com.trackflow.project.dto;

import lombok.Data;

/**
 * 更新项目时间追踪设置 DTO
 */
@Data
public class UpdateTimeTrackingSettingsDTO {

    /** 是否启用时间追踪（null 表示不修改） */
    private Boolean enabled;
}

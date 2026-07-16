package com.trackflow.sprint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintDTO {

    @NotBlank(message = "Sprint名称不能为空")
    @Size(max = 200)
    private String name;

    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * 是否将当前活跃 Sprint 中的未完成工单移入新 Sprint。
     * 仅在项目存在 active Sprint 且其中有未关闭工单时有效。
     */
    private Boolean moveUnresolvedIssues;

    /**
     * 是否将新 Sprint 设为项目的默认 Sprint。
     * 设为默认后，该项目新创建的工单自动归属此 Sprint。
     */
    private Boolean setAsDefault;
}

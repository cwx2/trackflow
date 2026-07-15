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
}

package com.trackflow.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建仪表盘 DTO
 */
@Data
public class CreateDashboardDTO {

    @NotBlank(message = "仪表盘名称不能为空")
    @Size(max = 200, message = "仪表盘名称最长200字")
    private String name;

    @Size(max = 2000, message = "描述最长2000字")
    private String description;

    private Boolean shared;
}

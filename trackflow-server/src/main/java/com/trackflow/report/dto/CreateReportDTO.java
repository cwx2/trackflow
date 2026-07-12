package com.trackflow.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建报表 DTO
 */
@Data
public class CreateReportDTO {

    @NotBlank(message = "报表名称不能为空")
    @Size(max = 200, message = "报表名称不能超过200字符")
    private String name;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "报表类型不能为空")
    private String type;       // issue_count / burndown / by_assignee / by_status / custom

    private String config;     // JSON: filters + groupBy + chartType

    private Boolean shared;
}

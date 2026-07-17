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

    /**
     * 报表类型，允许值：issue_count / by_status / by_assignee / by_priority / by_type / burndown / custom
     * @see com.trackflow.report.entity.ReportType
     */
    @NotBlank(message = "报表类型不能为空")
    private String type;

    /**
     * 报表配置（JSON），包含 groupBy（分组维度）等参数。
     * groupBy 允许值：status / assignee / priority / type
     * @see com.trackflow.report.entity.ReportGroupBy
     */
    private String config;

    private Boolean shared;
}


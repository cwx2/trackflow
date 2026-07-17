package com.trackflow.report.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新报表 DTO
 * 所有字段可选——只传需要修改的字段（PATCH 语义）
 */
@Data
public class UpdateReportDTO {

    @Size(max = 200, message = "报表名称不能超过200字符")
    private String name;

    /**
     * 报表类型，允许值：issue_count / by_status / by_assignee / by_priority / by_type / burndown / custom
     * @see com.trackflow.report.entity.ReportType
     */
    private String type;

    /**
     * 报表配置（JSON），包含 groupBy（分组维度）等参数。
     * @see com.trackflow.report.entity.ReportGroupBy
     */
    private String config;

    /**
     * 是否共享给项目其他成员
     */
    private Boolean shared;
}

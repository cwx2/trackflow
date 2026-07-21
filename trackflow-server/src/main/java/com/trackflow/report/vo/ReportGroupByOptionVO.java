package com.trackflow.report.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报表可用分组维度 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportGroupByOptionVO {

    /** 维度值（如 "status", "cf_2077320550062825474"） */
    private String value;

    /** 显示标签（如 "按状态", "平台"） */
    private String label;

    /** 维度类别: "builtin"（内置）或 "custom_field"（自定义字段） */
    private String category;

    /** 自定义字段格式（仅 category=custom_field 时有值），如 "list", "string", "user" */
    private String fieldFormat;
}

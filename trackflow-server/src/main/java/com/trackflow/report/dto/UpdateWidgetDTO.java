package com.trackflow.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新微件 DTO（配置/位置/大小）
 */
@Data
public class UpdateWidgetDTO {

    @Size(max = 200, message = "微件标题最长200字")
    private String title;

    /** 微件配置 JSON */
    private String config;

    /** 关联报表 ID（报表类微件） */
    private Long reportId;

    /** 是否清除关联报表（设为 true 时将 reportId 置空） */
    private Boolean clearReportId;

    @Min(value = 0, message = "位置X不能为负")
    private Integer positionX;

    @Min(value = 0, message = "位置Y不能为负")
    private Integer positionY;

    @Min(value = 1, message = "宽度最小为1")
    @Max(value = 12, message = "宽度最大为12")
    private Integer width;

    @Min(value = 1, message = "高度最小为1")
    @Max(value = 12, message = "高度最大为12")
    private Integer height;
}

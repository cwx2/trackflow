package com.trackflow.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加微件 DTO
 */
@Data
public class CreateWidgetDTO {

    @NotBlank(message = "微件类型不能为空")
    @Size(max = 50, message = "微件类型最长50字")
    private String widgetType;

    @Size(max = 200, message = "微件标题最长200字")
    private String title;

    /** 微件配置 JSON */
    private String config;

    /** 关联报表 ID（报表类微件） */
    private Long reportId;

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

package com.trackflow.timeentry.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.Map;

/**
 * 停止计时器的请求 DTO。
 * 用户可选择覆盖自动计算的 duration，或补充描述/属性。
 */
@Data
public class StopTimerDTO {

    /**
     * 手动覆盖的时长（分钟）。
     * 若为 null，则由系统根据 createdAt 到当前时刻自动计算。
     */
    @Min(value = 1, message = "时长至少1分钟")
    @Max(value = 1440, message = "单条工时不能超过1440分钟（24小时）")
    private Integer duration;

    /**
     * 描述（可覆盖启动时的描述）
     */
    private String description;

    /**
     * 工作项属性值（可覆盖/补充启动时的值）
     */
    private Map<String, String> attributeValues;
}

package com.trackflow.timeentry.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 启动计时器的请求 DTO。
 * 计时器是一条 ongoing=true 的 time_entry，duration 为 NULL，
 * 系统根据 created_at 实时计算已用时间。
 */
@Data
public class StartTimerDTO {

    @NotNull(message = "工单ID不能为空")
    private Long issueId;

    /**
     * 描述（可选，可后续在停止时补充）
     */
    private String description;

    /**
     * 工作项属性值：key=attributeId, value=valueId（可选）
     */
    private Map<String, String> attributeValues;
}

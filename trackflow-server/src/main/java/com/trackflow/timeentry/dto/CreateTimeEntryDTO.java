package com.trackflow.timeentry.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreateTimeEntryDTO {
    @NotNull(message = "工单ID不能为空")
    private Long issueId;

    @NotNull(message = "日期不能为空")
    private String workDate;

    @NotNull(message = "时长不能为空")
    @Min(value = 1, message = "时长至少1分钟")
    private Integer duration;   // minutes

    private Integer startTime;  // minutes from midnight
    private String workType;
    private String description;

    /**
     * 工作项属性值：key=attributeId, value=valueId
     * 前端传入格式：{"1234": "5678"} 表示属性 1234 选择了值 5678
     */
    private Map<String, String> attributeValues;
}

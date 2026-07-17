package com.trackflow.timeentry.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateTimeEntryDTO {
    private Long issueId;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式必须为 yyyy-MM-dd")
    private String workDate;

    @Min(value = 1, message = "时长至少1分钟")
    @Max(value = 1440, message = "单条工时不能超过1440分钟（24小时）")
    private Integer duration;

    @Min(value = 0, message = "开始时间不能为负数")
    @Max(value = 1439, message = "开始时间不能超过1439（23:59）")
    private Integer startTime;

    private String description;

    /**
     * 工作项属性值：key=attributeId, value=valueId
     */
    private Map<String, String> attributeValues;
}

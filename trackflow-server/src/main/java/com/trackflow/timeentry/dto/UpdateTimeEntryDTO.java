package com.trackflow.timeentry.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateTimeEntryDTO {
    private Long issueId;
    private String workDate;

    @Min(value = 1, message = "时长至少1分钟")
    private Integer duration;

    private Integer startTime;
    private String workType;
    private String description;

    /**
     * 工作项属性值：key=attributeId, value=valueId
     */
    private Map<String, String> attributeValues;
}

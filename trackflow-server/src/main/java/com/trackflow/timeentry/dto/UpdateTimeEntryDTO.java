package com.trackflow.timeentry.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateTimeEntryDTO {
    private Long issueId;
    private String workDate;

    @Min(value = 1, message = "时长至少1分钟")
    private Integer duration;

    private Integer startTime;
    private String workType;
    private String description;
}

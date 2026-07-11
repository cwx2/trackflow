package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransitStatusDTO {
    @NotNull(message = "目标状态ID不能为空")
    private Long statusId;
    private String comment;
}

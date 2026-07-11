package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignIssueDTO {
    @NotNull(message = "负责人ID不能为空")
    private Long assigneeId;
}

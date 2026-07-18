package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveIssueDTO {

    @NotNull(message = "目标项目ID不能为空")
    private Long targetProjectId;
}

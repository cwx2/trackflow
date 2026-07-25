package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateIssueLinkDTO {
    @NotNull(message = "目标Issue ID不能为空")
    private Long targetIssueId;

    @NotBlank(message = "关联类型不能为空")
    private String linkType;
}

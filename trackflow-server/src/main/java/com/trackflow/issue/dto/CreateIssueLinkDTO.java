package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateIssueLinkDTO {
    @NotNull(message = "目标Issue ID不能为空")
    private Long targetIssueId;

    @NotBlank(message = "关联类型不能为空")
    @Pattern(regexp = "^(blocks|blocked_by|duplicates|duplicated_by|parent_of|child_of|relates_to)$",
             message = "关联类型必须为: blocks, blocked_by, duplicates, duplicated_by, parent_of, child_of, relates_to")
    private String linkType;
}

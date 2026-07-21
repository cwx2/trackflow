package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class CreateIssueDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 500, message = "标题不能超过500字符")
    private String title;

    private String description;
    private String issueType;
    private String priority;
    private Long assigneeId;
    private Long sprintId;
    private Long parentId;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Map<String, String> customFields;
}

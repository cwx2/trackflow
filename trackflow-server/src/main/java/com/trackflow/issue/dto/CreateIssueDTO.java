package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * 创建时指定初始状态 ID（可选）。
     * 若为 null，则使用系统默认状态（isDefault=true）。
     */
    private Long statusId;

    private Long assigneeId;
    private Long sprintId;
    private Long parentId;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Map<String, String> customFields;

    /**
     * 创建工单时同时绑定的标签 ID 列表（可选）。
     */
    private List<Long> tagIds;

    /**
     * 创建工单时同时建立的关联（可选）。
     * 支持在创建工单的同时指定与已有工单的关联关系。
     */
    @Valid
    private List<CreateIssueLinkDTO> links;
}

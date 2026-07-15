package com.trackflow.sprint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 完成 Sprint 时的请求体：指定未完成工单的处理方式
 */
@Data
public class CompleteSprintDTO {

    /**
     * 未完成工单的处理方式：
     * - "next_sprint": 移入指定的目标 Sprint
     * - "backlog": 移回 Backlog（清空 sprint_id）
     */
    @NotBlank(message = "请选择未完成工单的处理方式")
    @Pattern(regexp = "next_sprint|backlog", message = "moveOption 只能是 next_sprint 或 backlog")
    private String moveOption;

    /**
     * 目标 Sprint ID（当 moveOption = "next_sprint" 时必填）
     */
    private Long targetSprintId;
}

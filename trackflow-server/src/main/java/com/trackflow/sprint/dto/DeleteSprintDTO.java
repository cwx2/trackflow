package com.trackflow.sprint.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 删除 Sprint 时的请求体：指定关联工单的处理方式。
 * 当 Sprint 包含工单时，必须提供 moveOption。
 */
@Data
public class DeleteSprintDTO {

    /**
     * 关联工单的处理方式：
     * - "backlog": 移回 Backlog（清空 sprint_id）
     * - "next_sprint": 移入指定的目标 Sprint
     */
    @Pattern(regexp = "next_sprint|backlog", message = "moveOption 只能是 next_sprint 或 backlog")
    private String moveOption;

    /**
     * 目标 Sprint ID（当 moveOption = "next_sprint" 时必填）
     */
    private Long targetSprintId;
}

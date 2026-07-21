package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动单个工单排序位置 DTO
 */
@Data
public class MoveOrderDTO {

    /** 排序上下文类型：project / query */
    @NotNull(message = "上下文类型不能为空")
    private String contextType;

    /** 上下文 ID */
    @NotNull(message = "上下文 ID 不能为空")
    private Long contextId;

    /** 被移动的工单 ID */
    @NotNull(message = "工单 ID 不能为空")
    private Long issueId;

    /** 目标位置（插入到哪个位置之前，0-based） */
    @NotNull(message = "目标位置不能为空")
    private Integer targetPosition;
}

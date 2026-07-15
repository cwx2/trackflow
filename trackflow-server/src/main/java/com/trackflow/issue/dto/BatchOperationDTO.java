package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量操作请求 DTO
 */
@Data
public class BatchOperationDTO {

    /**
     * 操作类型: status, assign, sprint, priority, delete
     */
    @NotNull(message = "操作类型不能为空")
    @Pattern(regexp = "status|assign|sprint|priority|delete", message = "操作类型必须为: status, assign, sprint, priority, delete")
    private String operation;

    /**
     * 要操作的 Issue ID 列表
     */
    @NotEmpty(message = "工单列表不能为空")
    @Size(max = 50, message = "批量操作最多支持 50 个工单")
    private List<Long> issueIds;

    /**
     * 目标状态 ID（operation=status 时必填）
     */
    private Long statusId;

    /**
     * 目标负责人 ID（operation=assign 时必填，0 表示取消分配）
     */
    private Long assigneeId;

    /**
     * 目标 Sprint ID（operation=sprint 时必填，0 表示移除 Sprint）
     */
    private Long sprintId;

    /**
     * 目标优先级（operation=priority 时必填）
     */
    private String priority;
}

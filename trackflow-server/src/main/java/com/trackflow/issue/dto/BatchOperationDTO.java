package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批量操作请求 DTO
 */
@Data
public class BatchOperationDTO {

    /**
     * 操作类型: status, assign, sprint, priority, delete
     */
    @NotNull(message = "操作类型不能为空")
    @Pattern(regexp = "status|assign|sprint|priority|delete|restore", message = "操作类型必须为: status, assign, sprint, priority, delete, restore")
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

    /**
     * 状态转换备注（operation=status 时可选）。
     * 非空时会作为评论记录到活动日志。
     */
    private String comment;

    /**
     * 乐观锁版本号映射（issueId → version）。
     * 用于并发冲突检测：如果某工单的 version 与数据库不一致，该工单操作失败但不影响其他工单。
     * 为 null 时不做乐观锁校验（向后兼容）。
     */
    private Map<Long, Integer> versions;
}

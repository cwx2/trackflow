package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransitStatusDTO {
    @NotNull(message = "目标状态ID不能为空")
    private Long statusId;
    private String comment;

    /**
     * 显式指定的 assignee（manual override）。
     * 为 null 且 assigneeExplicit=true 时表示用户主动取消分配。
     */
    private Long assigneeId;

    /**
     * 是否用户明确设置了 assignee。
     * true = 用户主动选择了 assignee（即使为 null 也代表 unassign）。
     * false/null = 未指定，由 TransitionActionEngine 自动分配。
     */
    private Boolean assigneeExplicit;

    /**
     * 是否强制关闭（忽略子任务未完成的警告）。
     * 当父工单有未关闭子任务、用户确认后强制关闭时设为 true。
     */
    private Boolean force;

    /**
     * 是否强制超越 WIP 限制。
     * 当目标列已达到 WIP 上限，用户确认后强制移入时设为 true。
     */
    private Boolean forceWip;

    /**
     * 乐观锁版本号（前端传入，用于并发控制）。
     */
    private Integer version;
}

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
}

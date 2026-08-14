package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

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
     * 是否强制允许描述为空时转换到测试状态。
     * 当工单描述为空但用户确认要继续转换到 Testing 状态时设为 true。
     */
    private Boolean forceDescEmpty;

    /**
     * 乐观锁版本号（前端传入，用于并发控制）。
     */
    private Integer version;

    /**
     * 状态转换时同步提交的自定义字段值（key=fieldId 字符串, value=字段值）。
     * 用于在确认弹窗中一次性填写目标状态的必填字段，实现原子化操作。
     * 为 null 或空时不做字段更新。
     */
    private Map<String, String> customFieldValues;
}

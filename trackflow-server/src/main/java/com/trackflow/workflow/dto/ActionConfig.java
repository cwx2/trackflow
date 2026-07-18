package com.trackflow.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Auto-Assign 动作配置 —— 对应 transition_action.action_config JSONB 结构。
 * <p>
 * JSON 示例:
 * <pre>
 * {
 *   "strategy": "role_based",
 *   "role_id": 6,
 *   "mode": "round_robin",
 *   "fallback_strategy": "project_lead"
 * }
 * </pre>
 */
@Data
public class ActionConfig {

    /** 分配策略: specific_user, role_based, previous_assignee, reporter, project_lead */
    private String strategy;

    /** 目标用户 ID（strategy = specific_user 时必填） */
    @JsonProperty("user_id")
    private Long userId;

    /** 角色 ID（strategy = role_based 时必填） */
    @JsonProperty("role_id")
    private Long roleId;

    /** 分配模式: round_robin 或 least_loaded（strategy = role_based 时必填） */
    private String mode;

    /** 回退策略：当主策略无法解析目标用户时使用 */
    @JsonProperty("fallback_strategy")
    private String fallbackStrategy;

    /**
     * 加权配置（mode = weighted_round_robin 时使用）。
     * key = 用户 ID（字符串），value = 权重（正整数，越大分配越多）。
     * 例如：{"12345": 3, "67890": 1} 表示用户 12345 被分配的概率是 67890 的 3 倍。
     */
    private java.util.Map<String, Integer> weights;

    /**
     * 是否排除报告人（reporter）作为自动分配的候选人。
     * <p>
     * 默认 true：Round-robin / Least-loaded / Weighted 模式下，
     * 如果报告人也在候选池中，将被跳过（避免自己分配给自己）。
     * <p>
     * 设为 false 可关闭此行为（极少见场景）。
     * 参考 YouTrack Workflow 的 excludeReporter 选项。
     */
    @JsonProperty("exclude_reporter")
    private Boolean excludeReporter;
}

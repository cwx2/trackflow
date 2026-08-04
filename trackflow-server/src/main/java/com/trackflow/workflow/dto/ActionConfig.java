package com.trackflow.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 动作配置 —— 对应 transition_action.action_config JSONB 结构。
 * <p>
 * auto_assign JSON 示例:
 * <pre>
 * {
 *   "strategy": "role_based",
 *   "role_id": 6,
 *   "mode": "round_robin",
 *   "fallback_strategy": "project_lead"
 * }
 * </pre>
 * <p>
 * add_comment JSON 示例:
 * <pre>
 * {
 *   "comment_template": "状态已自动变更，请及时处理。"
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

    /**
     * 评论模板文本（action_type = add_comment 时使用）。
     * 支持占位符：{issue_key}, {old_status}, {new_status}（留待未来扩展）。
     */
    @JsonProperty("comment_template")
    private String commentTemplate;

    /**
     * 是否强制重新分配（strategy = role_based 时使用）。
     * <p>
     * 默认 false：如果当前负责人已经是目标角色的成员，保留现有负责人，不执行轮询分配。
     * 这符合 YouTrack 的行为——轮询自动分配规则仅在没有适合角色的负责人时才触发。
     * <p>
     * 设为 true：强制执行轮询分配，即使当前负责人已属于目标角色也会被替换。
     * <p>
     * 参考 YouTrack Workflow 的 guard 条件 `!issue.fields.Assignee`。
     */
    @JsonProperty("force_reassign")
    private Boolean forceReassign;

    // ===== require_field 动作专用字段 =====

    /**
     * 必填字段 ID（action_type = require_field 时必填）。
     * 指向 custom_field_definition.id。
     * <p>
     * 当状态转换到目标状态时，如果此字段为空，将显示警告并阻止转换。
     * 参考 YouTrack Workflow 的 issue.fields.required(field, message) 方法。
     * <p>
     * 注意：此字段使用 Object 类型存储 JSON 值，然后通过 getRequiredFieldId() 转为 Long。
     * 原因是 Jackson 解析 JSONB 中的大整数时可能存在精度丢失
     * （当数值超过 JS Number.MAX_SAFE_INTEGER 时）。
     * 使用 Object 类型可以捕获原始值（Number 或 String），再安全地转换。
     */
    @JsonProperty("required_field_id")
    private Object requiredFieldIdRaw;

    /**
     * 获取必填字段 ID（安全转换，避免精度丢失）。
     */
    public Long getRequiredFieldId() {
        if (requiredFieldIdRaw == null) {
            return null;
        }
        if (requiredFieldIdRaw instanceof Number) {
            // Jackson 可能将大整数解析为 BigInteger 或 Double
            // 对于超过 Long.MAX_VALUE 的情况，longValue() 会溢出，但我们的 ID 不会那么大
            return ((Number) requiredFieldIdRaw).longValue();
        }
        if (requiredFieldIdRaw instanceof String) {
            try {
                return Long.parseLong((String) requiredFieldIdRaw);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 必填字段名称（可选，用于显示警告消息）。
     * 如果不填，将自动从 custom_field_definition 获取。
     */
    @JsonProperty("required_field_name")
    private String requiredFieldName;

    /**
     * 校验失败时的警告消息模板（可选）。
     * 支持占位符：{field_name}（字段名称）
     * <p>
     * 默认消息："请先填写「{field_name}」字段"
     */
    @JsonProperty("warning_message")
    private String warningMessage;

    // ===== add_tag 动作专用字段 =====

    /**
     * 标签 ID（action_type = add_tag 时可选）。
     * 如果提供了 tag_id，则直接使用该标签。
     */
    @JsonProperty("tag_id")
    private Long tagId;

    /**
     * 标签名称（action_type = add_tag 时可选）。
     * 如果 tag_id 未提供或找不到，按项目+名称查找标签；
     * 如果标签不存在则自动创建。
     */
    @JsonProperty("tag_name")
    private String tagName;
}

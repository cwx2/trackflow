package com.trackflow.common.rule;

/**
 * 条件评估上下文 —— 携带字段变更信息和评论内容等运行时数据。
 * <p>
 * 不同触发场景需要不同的上下文：
 * <ul>
 *   <li>field_changed：提供 changedField + oldValue</li>
 *   <li>comment_added：提供 commentContent</li>
 *   <li>on_schedule / issue_created：通常不需要额外上下文</li>
 * </ul>
 *
 * @param changedField   变更的字段名（field_changed 触发器使用）
 * @param oldValue       变更前的旧值（field_changed 触发器使用）
 * @param commentContent 评论内容（comment_added 触发器使用）
 * @param hasUserContext 是否有当前用户上下文（on_schedule 场景为 false）
 *
 * @author TrackFlow
 * @since 1.0
 */
public record EvaluationContext(
        String changedField,
        String oldValue,
        String commentContent,
        boolean hasUserContext
) {

    /**
     * 空上下文（无变更字段、无评论、有用户上下文）。
     * 适用于 issue_created 等不涉及字段变更的场景。
     */
    public static final EvaluationContext EMPTY = new EvaluationContext(null, null, null, true);

    /**
     * 无用户上下文（定时任务场景）。
     */
    public static final EvaluationContext SCHEDULED = new EvaluationContext(null, null, null, false);

    /**
     * 字段变更场景。
     */
    public static EvaluationContext fieldChanged(String changedField, String oldValue) {
        return new EvaluationContext(changedField, oldValue, null, true);
    }

    /**
     * 评论新增场景。
     */
    public static EvaluationContext commentAdded(String commentContent) {
        return new EvaluationContext(null, null, commentContent, true);
    }
}

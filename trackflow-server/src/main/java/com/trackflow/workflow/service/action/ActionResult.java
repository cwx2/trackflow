package com.trackflow.workflow.service.action;

/**
 * 动作执行结果 — 封装执行后的状态信息。
 *
 * @author TrackFlow
 * @since 1.0
 */
public record ActionResult(
        /**
         * 是否修改了 Issue 实体字段（需要在所有动作执行完后统一 updateById）。
         */
        boolean issueModified,

        /**
         * 是否应阻断后续动作的执行（如 require_field 检查失败时）。
         */
        boolean haltExecution
) {
    /** 未修改、不阻断 */
    public static final ActionResult NONE = new ActionResult(false, false);

    /** 修改了 Issue 实体 */
    public static final ActionResult MODIFIED = new ActionResult(true, false);

    /** 阻断后续动作执行 */
    public static final ActionResult HALT = new ActionResult(false, true);

    public static ActionResult of(boolean modified) {
        return modified ? MODIFIED : NONE;
    }
}

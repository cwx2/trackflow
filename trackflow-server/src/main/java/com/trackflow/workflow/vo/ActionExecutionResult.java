package com.trackflow.workflow.vo;

import lombok.Data;

/**
 * 工作流转换动作执行结果。
 * <p>
 * 由 TransitionActionEngine 返回，IssueService 传递给 Controller，
 * 最终包含在状态转换 API 响应中让前端感知自动化动作是否生效。
 */
@Data
public class ActionExecutionResult {

    /**
     * 执行结果枚举
     */
    public enum Outcome {
        /** 自动分配成功 */
        ASSIGNED,
        /** 用户手动指定 assignee，跳过自动分配 */
        MANUAL_OVERRIDE,
        /** 策略执行失败（主策略+fallback 都无法解析） */
        STRATEGY_FAILED,
        /** 无匹配动作配置 */
        NO_ACTIONS,
        /** 动作执行抛出异常 */
        EXECUTION_ERROR,
        /** 自动添加评论成功 */
        COMMENT_ADDED
    }

    /** 是否有动作被成功执行 */
    private boolean executed;

    /** 动作类型（如 "auto_assign"） */
    private String actionType;

    /** 执行结果 */
    private Outcome outcome;

    /** 分配给了谁的用户 ID（成功时非空），VO 中用 String 防精度丢失 */
    private String newAssigneeId;

    /** 分配给了谁的显示名称（成功时非空） */
    private String newAssigneeName;

    /** 使用了哪个策略 */
    private String strategyUsed;

    // ===== 工厂方法 =====

    public static ActionExecutionResult assigned(Long assigneeId, String assigneeName, String strategy) {
        ActionExecutionResult r = new ActionExecutionResult();
        r.setExecuted(true);
        r.setActionType("auto_assign");
        r.setOutcome(Outcome.ASSIGNED);
        r.setNewAssigneeId(assigneeId != null ? String.valueOf(assigneeId) : null);
        r.setNewAssigneeName(assigneeName);
        r.setStrategyUsed(strategy);
        return r;
    }

    public static ActionExecutionResult manualOverride(Long assigneeId, String assigneeName) {
        ActionExecutionResult r = new ActionExecutionResult();
        r.setExecuted(true);
        r.setActionType("auto_assign");
        r.setOutcome(Outcome.MANUAL_OVERRIDE);
        r.setNewAssigneeId(assigneeId != null ? String.valueOf(assigneeId) : null);
        r.setNewAssigneeName(assigneeName);
        return r;
    }

    public static ActionExecutionResult strategyFailed() {
        ActionExecutionResult r = new ActionExecutionResult();
        r.setExecuted(false);
        r.setActionType("auto_assign");
        r.setOutcome(Outcome.STRATEGY_FAILED);
        return r;
    }

    public static ActionExecutionResult noActions() {
        ActionExecutionResult r = new ActionExecutionResult();
        r.setExecuted(false);
        r.setOutcome(Outcome.NO_ACTIONS);
        return r;
    }

    public static ActionExecutionResult executionError() {
        ActionExecutionResult r = new ActionExecutionResult();
        r.setExecuted(false);
        r.setActionType("auto_assign");
        r.setOutcome(Outcome.EXECUTION_ERROR);
        return r;
    }

    public static ActionExecutionResult commentAdded() {
        ActionExecutionResult r = new ActionExecutionResult();
        r.setExecuted(true);
        r.setActionType("add_comment");
        r.setOutcome(Outcome.COMMENT_ADDED);
        return r;
    }
}

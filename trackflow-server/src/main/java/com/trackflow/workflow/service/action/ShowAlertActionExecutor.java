package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 显示提醒动作执行器 — 创建站内通知提示。
 * <p>
 * style 支持: "acknowledgment"（普通提示）和 "error"（错误提示）。
 * 通过站内通知实现，前端通知面板实时展示。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class ShowAlertActionExecutor extends WorkflowActionSupport {

    @Override
    public String actionType() {
        return "show_alert";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String style = textOf(actionConfig, "style");
        if (style == null || style.isBlank()) style = "acknowledgment";
        String message = textOf(actionConfig, "message");
        if (message == null || message.isBlank()) {
            message = "规则 '" + rule.getName() + "' 已执行";
        }
        message = interpolateVariables(message, issue, rule);

        String targetStr = textOf(actionConfig, "target");
        Long targetUserId = resolveUserTarget(targetStr, issue, rule);

        createAlertNotification(targetUserId, issue, rule, message, style);
        logActivity(issue.getId(), rule, "alert_shown", null, null, style + ": " + message);
        log.info("[RuleEngine] show_alert: '{}' for issue {} by rule '{}' (id={})",
                message, issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.NONE;
    }
}

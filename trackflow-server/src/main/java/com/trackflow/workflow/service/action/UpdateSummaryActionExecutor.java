package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 更新标题动作执行器 — 修改工单标题（支持变量插值）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class UpdateSummaryActionExecutor extends WorkflowActionSupport {

    @Override
    public String actionType() {
        return "update_summary";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String value = textOf(actionConfig, "value");
        if (value == null || value.isBlank()) {
            log.warn("[RuleEngine] update_summary: value is empty in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }
        value = interpolateVariables(value, issue, rule);
        String oldTitle = issue.getTitle();
        if (Objects.equals(oldTitle, value)) return ActionResult.NONE;

        issue.setTitle(value);
        logActivity(issue.getId(), rule, "updated", "title", oldTitle, value);
        log.info("[RuleEngine] update_summary: changed title on issue {} by rule '{}' (id={})",
                issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.MODIFIED;
    }
}

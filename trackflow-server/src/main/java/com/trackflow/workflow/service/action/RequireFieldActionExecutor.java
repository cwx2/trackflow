package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 必填字段检查动作执行器 — 检查字段是否已填写，否则阻断后续动作执行。
 * <p>
 * 注意：规则引擎在事务提交后异步执行，此动作无法阻断用户原始操作，
 * 而是阻断本规则中后续动作的执行，并记录审计日志和发送通知。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class RequireFieldActionExecutor extends WorkflowActionSupport {

    public RequireFieldActionExecutor(IssueActivityMapper activityMapper,
                                      SysUserMapper sysUserMapper,
                                      NotificationMapper notificationMapper,
                                      ProjectService projectService) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
    }

    @Override
    public String actionType() {
        return "require_field";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String field = textOf(actionConfig, "field");
        if (field == null || field.isBlank()) {
            log.warn("[RuleEngine] require_field: field is not specified in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE; // 配置错误不阻断
        }
        String actual = fieldValue(issue, field);
        if (actual == null || actual.isBlank()) {
            String errorMessage = textOf(actionConfig, "errorMessage");
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "字段 '" + field + "' 为空，规则动作被阻断";
            }
            errorMessage = interpolateVariables(errorMessage, issue, rule);
            logActivity(issue.getId(), rule, "action_blocked", field, null, errorMessage);
            log.info("[RuleEngine] require_field: field '{}' is empty on issue {}, blocking actions in rule '{}' (id={})",
                    field, issue.getIssueKey(), rule.getName(), rule.getId());

            // 发送站内通知给规则创建者
            createAlertNotification(rule.getCreatedBy(), issue, rule,
                    "规则阻断: " + errorMessage, "error");
            return ActionResult.HALT;
        }
        return ActionResult.NONE;
    }
}

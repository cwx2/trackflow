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

import java.util.Objects;

/**
 * 更新描述动作执行器 — 修改工单描述（支持变量插值）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class UpdateDescriptionActionExecutor extends WorkflowActionSupport {

    public UpdateDescriptionActionExecutor(IssueActivityMapper activityMapper,
                                           SysUserMapper sysUserMapper,
                                           NotificationMapper notificationMapper,
                                           ProjectService projectService) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
    }

    @Override
    public String actionType() {
        return "update_description";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String value = textOf(actionConfig, "value");
        if (value == null) {
            log.warn("[RuleEngine] update_description: value is null in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }
        value = interpolateVariables(value, issue, rule);
        String oldDesc = issue.getDescription();
        if (Objects.equals(oldDesc, value)) return ActionResult.NONE;

        issue.setDescription(value);
        logActivity(issue.getId(), rule, "updated", "description", oldDesc, value);
        log.info("[RuleEngine] update_description: changed description on issue {} by rule '{}' (id={})",
                issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.MODIFIED;
    }
}

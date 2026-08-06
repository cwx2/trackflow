package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.service.IssueService;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 移动到项目动作执行器 — 将工单移动到另一个项目。
 * <p>
 * 委托给 IssueService 的自动化移动方法（跳过权限检查）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class MoveToProjectActionExecutor extends WorkflowActionSupport {

    private final IssueService issueService;

    public MoveToProjectActionExecutor(IssueActivityMapper activityMapper,
                                       SysUserMapper sysUserMapper,
                                       NotificationMapper notificationMapper,
                                       ProjectService projectService,
                                       IssueService issueService) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.issueService = issueService;
    }

    @Override
    public String actionType() {
        return "move_to_project";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String targetProjectIdStr = textOf(actionConfig, "targetProjectId");
        if (targetProjectIdStr == null || targetProjectIdStr.isBlank()) {
            log.warn("[RuleEngine] move_to_project: targetProjectId is empty in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        Long targetProjectId;
        try {
            targetProjectId = Long.parseLong(targetProjectIdStr);
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] move_to_project: invalid targetProjectId '{}' in rule '{}' (id={})",
                    targetProjectIdStr, rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        try {
            issueService.moveToProjectByAutomation(issue.getId(), targetProjectId, rule.getCreatedBy());
            log.info("[RuleEngine] move_to_project: moved issue {} to project {} by rule '{}' (id={})",
                    issue.getIssueKey(), targetProjectId, rule.getName(), rule.getId());
        } catch (Exception e) {
            log.warn("[RuleEngine] move_to_project: failed for issue {} in rule '{}': {}",
                    issue.getIssueKey(), rule.getName(), e.getMessage());
        }
        return ActionResult.NONE;
    }
}

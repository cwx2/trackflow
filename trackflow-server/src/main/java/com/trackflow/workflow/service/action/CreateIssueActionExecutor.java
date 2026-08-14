package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.PriorityFieldService;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowInitialStatus;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowInitialStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 创建工单动作执行器 — 创建新工单并触发 on-create 链式规则。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class CreateIssueActionExecutor extends WorkflowActionSupport {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final WorkflowInitialStatusMapper initialStatusMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PriorityFieldService priorityFieldService;

    public CreateIssueActionExecutor(IssueActivityMapper activityMapper,
                                     SysUserMapper sysUserMapper,
                                     NotificationMapper notificationMapper,
                                     ProjectService projectService,
                                     IssueMapper issueMapper,
                                     IssueStatusMapper statusMapper,
                                     WorkflowInitialStatusMapper initialStatusMapper,
                                     ApplicationEventPublisher eventPublisher,
                                     PriorityFieldService priorityFieldService) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.issueMapper = issueMapper;
        this.statusMapper = statusMapper;
        this.initialStatusMapper = initialStatusMapper;
        this.eventPublisher = eventPublisher;
        this.priorityFieldService = priorityFieldService;
    }

    @Override
    public String actionType() {
        return "create_issue";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String summary = textOf(actionConfig, "summary");
        if (summary == null || summary.isBlank()) {
            log.warn("[RuleEngine] create_issue: summary is empty in rule '{}' (id={})", rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        summary = interpolateVariables(summary, issue, rule);
        String description = textOf(actionConfig, "description");
        if (description != null) {
            description = interpolateVariables(description, issue, rule);
        }

        String issueType = textOf(actionConfig, "issueType");
        if (issueType == null || issueType.isBlank()) issueType = "Task";

        Long projectId = issue.getProjectId();
        String projectIdStr = textOf(actionConfig, "projectId");
        if (projectIdStr != null && !"same".equals(projectIdStr)) {
            try {
                projectId = Long.parseLong(projectIdStr);
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] create_issue: invalid projectId '{}' in rule '{}'", projectIdStr, rule.getName());
            }
        }

        String priority = textOf(actionConfig, "priority");
        if (priority == null || priority.isBlank()) priority = priorityFieldService.getDefaultPriority(projectId);

        com.trackflow.project.entity.Project project = projectService.getById(projectId);
        if (project == null) {
            log.warn("[RuleEngine] create_issue: project {} not found in rule '{}'", projectId, rule.getName());
            return ActionResult.NONE;
        }
        int seq = projectService.nextIssueSequence(projectId);
        String issueKey = project.getKey() + "-" + seq;

        Issue newIssue = new Issue();
        newIssue.setProjectId(projectId);
        newIssue.setIssueKey(issueKey);
        newIssue.setTitle(summary);
        newIssue.setDescription(description);
        newIssue.setIssueType(issueType);
        newIssue.setPriority(priority);
        newIssue.setReporterId(rule.getCreatedBy());
        newIssue.setCreatedBy(rule.getCreatedBy());

        Long initialStatusId = resolveInitialStatus(projectId, issueType);
        newIssue.setStatusId(initialStatusId);

        // Copy sprint from trigger if in same project
        if (Objects.equals(projectId, issue.getProjectId()) && issue.getSprintId() != null) {
            newIssue.setSprintId(issue.getSprintId());
        }

        // Set parent if specified
        String parentRef = textOf(actionConfig, "parent");
        if ("trigger".equals(parentRef)) {
            newIssue.setParentId(issue.getId());
        }

        issueMapper.insert(newIssue);
        context.addCreatedIssue(newIssue);

        logActivity(newIssue.getId(), rule, "created", null, null, null);
        log.info("[RuleEngine] create_issue: created {} (type={}, project={}) by rule '{}' (id={})",
                issueKey, issueType, projectId, rule.getName(), rule.getId());

        // 触发 on-create 链式规则
        eventPublisher.publishEvent(new WorkflowRuleEvent.IssueCreated(newIssue.getId(), newIssue.getProjectId()));

        return ActionResult.NONE;
    }

    private Long resolveInitialStatus(Long projectId, String issueType) {
        String effectiveIssueType = (issueType == null || issueType.isBlank()) ? "*" : issueType;

        if (projectId != null && !"*".equals(effectiveIssueType)) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .eq(WorkflowInitialStatus::getProjectId, projectId)
                            .eq(WorkflowInitialStatus::getIssueType, effectiveIssueType));
            if (config != null) return config.getStatusId();
        }
        if (projectId != null) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .eq(WorkflowInitialStatus::getProjectId, projectId)
                            .eq(WorkflowInitialStatus::getIssueType, "*"));
            if (config != null) return config.getStatusId();
        }
        if (!"*".equals(effectiveIssueType)) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .isNull(WorkflowInitialStatus::getProjectId)
                            .eq(WorkflowInitialStatus::getIssueType, effectiveIssueType));
            if (config != null) return config.getStatusId();
        }
        WorkflowInitialStatus config = initialStatusMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInitialStatus>()
                        .isNull(WorkflowInitialStatus::getProjectId)
                        .eq(WorkflowInitialStatus::getIssueType, "*"));
        if (config != null) return config.getStatusId();

        IssueStatus status = statusMapper.selectOne(new LambdaQueryWrapper<IssueStatus>()
                .eq(IssueStatus::getIsClosed, false)
                .orderByAsc(IssueStatus::getSortOrder)
                .last("LIMIT 1"));
        return status != null ? status.getId() : null;
    }
}

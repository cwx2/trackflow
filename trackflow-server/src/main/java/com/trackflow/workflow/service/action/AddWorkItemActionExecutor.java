package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.mapper.TimeEntryMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 添加工时记录动作执行器 — 为工单自动添加工时。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class AddWorkItemActionExecutor extends WorkflowActionSupport {

    private final TimeEntryMapper timeEntryMapper;

    public AddWorkItemActionExecutor(IssueActivityMapper activityMapper,
                                     SysUserMapper sysUserMapper,
                                     NotificationMapper notificationMapper,
                                     ProjectService projectService,
                                     TimeEntryMapper timeEntryMapper) {
        super(activityMapper, sysUserMapper, notificationMapper, projectService);
        this.timeEntryMapper = timeEntryMapper;
    }

    @Override
    public String actionType() {
        return "add_work_item";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String durationStr = textOf(actionConfig, "duration");
        if (durationStr == null || durationStr.isBlank()) {
            log.warn("[RuleEngine] add_work_item: duration is empty in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(durationStr);
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] add_work_item: invalid duration '{}' in rule '{}' (id={})",
                    durationStr, rule.getName(), rule.getId());
            return ActionResult.NONE;
        }
        if (durationMinutes <= 0 || durationMinutes > 1440) {
            log.warn("[RuleEngine] add_work_item: duration {} out of range [1,1440] in rule '{}' (id={})",
                    durationMinutes, rule.getName(), rule.getId());
            return ActionResult.NONE;
        }

        String description = textOf(actionConfig, "description");
        if (description != null) {
            description = interpolateVariables(description, issue, rule);
        }

        String dateStr = textOf(actionConfig, "date");
        LocalDate workDate;
        if ("today".equals(dateStr) || dateStr == null || dateStr.isBlank()) {
            workDate = LocalDate.now();
        } else {
            try {
                workDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                workDate = LocalDate.now();
            }
        }

        TimeEntry entry = new TimeEntry();
        entry.setIssueId(issue.getId());
        entry.setProjectId(issue.getProjectId());
        entry.setUserId(rule.getCreatedBy());
        entry.setLoggedBy(rule.getCreatedBy());
        entry.setWorkDate(workDate);
        entry.setDuration(durationMinutes);
        entry.setDescription(description != null ? description : "自动规则：" + rule.getName());
        entry.setOngoing(false);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        timeEntryMapper.insert(entry);

        logActivity(issue.getId(), rule, "time_logged", "duration", null, durationMinutes + "m");
        log.info("[RuleEngine] add_work_item: logged {}m on issue {} by rule '{}' (id={})",
                durationMinutes, issue.getIssueKey(), rule.getName(), rule.getId());
        return ActionResult.NONE;
    }
}

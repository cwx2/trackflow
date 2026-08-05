package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * 设置字段动作执行器 — 修改工单字段值。
 * <p>
 * 支持字段：priority、assignee、type、status、sprint、due_date
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class SetFieldActionExecutor extends WorkflowActionSupport {

    private static final Set<String> VALID_PRIORITIES = Set.of("Critical", "High", "Normal", "Low");

    @Autowired
    private IssueStatusMapper statusMapper;

    @Autowired
    private SprintMapper sprintMapper;

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public String actionType() {
        return "set_field";
    }

    @Override
    public ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                                ActionExecutionContext context) {
        String field = textOf(actionConfig, "field");
        String value = textOf(actionConfig, "value");
        if (field == null) return ActionResult.NONE;
        String old = fieldValue(issue, field);

        boolean modified = switch (field) {
            case "priority" -> {
                if (!validatePriority(value, rule)) yield false;
                issue.setPriority(value);
                yield true;
            }
            case "assignee", "assignee_id" -> {
                Long assigneeId = resolveAndValidateAssignee(value, issue.getProjectId(), rule);
                if (assigneeId == null && value != null && !value.isBlank()) yield false;
                issue.setAssigneeId(assigneeId);
                yield true;
            }
            case "type", "issue_type" -> {
                issue.setIssueType(value);
                yield true;
            }
            case "status", "status_id" -> {
                Long statusId = parseIdSafe(value, "status_id", rule);
                if (statusId == null && value != null && !value.isBlank()) yield false;
                if (statusId != null && !validateStatusExists(statusId, rule)) yield false;
                issue.setStatusId(statusId);
                yield true;
            }
            case "sprint", "sprint_id" -> {
                Long sprintId = parseIdSafe(value, "sprint_id", rule);
                if (sprintId == null && value != null && !value.isBlank()) yield false;
                if (sprintId != null && !validateSprint(sprintId, issue.getProjectId(), rule)) yield false;
                issue.setSprintId(sprintId);
                yield true;
            }
            case "due_date", "dueDate" -> {
                LocalDate dueDate = parseDateSafe(value, rule);
                if (dueDate == null && value != null && !value.isBlank()) yield false;
                issue.setDueDate(dueDate);
                yield true;
            }
            default -> {
                log.warn("[RuleEngine] set_field: unsupported field '{}' in rule '{}' (id={})",
                        field, rule.getName(), rule.getId());
                yield false;
            }
        };

        if (!modified) return ActionResult.NONE;

        logActivity(issue.getId(), rule, "updated", field, old, value);
        // 发布字段变更事件，支持链式规则触发
        String canonicalField = canonicalFieldName(field);
        eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(
                issue.getId(), issue.getProjectId(), canonicalField, old));
        return ActionResult.MODIFIED;
    }

    private String canonicalFieldName(String field) {
        return switch (field) {
            case "assignee" -> "assignee_id";
            case "type" -> "issue_type";
            case "status" -> "status_id";
            case "sprint" -> "sprint_id";
            case "dueDate" -> "due_date";
            default -> field;
        };
    }

    private boolean validatePriority(String value, WorkflowRule rule) {
        if (value == null || value.isBlank()) return true;
        if (!VALID_PRIORITIES.contains(value)) {
            log.warn("[RuleEngine] set_field priority: invalid value '{}' in rule '{}' (id={}). Valid values: {}",
                    value, rule.getName(), rule.getId(), VALID_PRIORITIES);
            return false;
        }
        return true;
    }

    private Long resolveAndValidateAssignee(String value, Long projectId, WorkflowRule rule) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;

        Long userId = null;
        try {
            userId = Long.parseLong(value);
        } catch (NumberFormatException e) {
            LambdaQueryWrapper<SysUser> uw = new LambdaQueryWrapper<>();
            uw.eq(SysUser::getUsername, value);
            SysUser user = sysUserMapper.selectOne(uw);
            if (user != null) userId = user.getId();
        }

        if (userId == null) {
            log.warn("[RuleEngine] set_field assignee: cannot resolve user '{}' in rule '{}' (id={})",
                    value, rule.getName(), rule.getId());
            return null;
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("[RuleEngine] set_field assignee: user id={} does not exist, rule '{}' (id={})",
                    userId, rule.getName(), rule.getId());
            return null;
        }
        if ("disabled".equals(user.getStatus())) {
            log.warn("[RuleEngine] set_field assignee: user '{}' (id={}) is disabled, rule '{}' (id={})",
                    user.getUsername(), userId, rule.getName(), rule.getId());
            return null;
        }

        if (!projectService.isProjectMember(userId, projectId)) {
            log.warn("[RuleEngine] set_field assignee: user '{}' (id={}) is not a member of project {}, rule '{}' (id={})",
                    user.getUsername(), userId, projectId, rule.getName(), rule.getId());
            return null;
        }
        return userId;
    }

    private Long parseIdSafe(String value, String fieldName, WorkflowRule rule) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] set_field {}: invalid numeric value '{}' in rule '{}' (id={})",
                    fieldName, value, rule.getName(), rule.getId());
            return null;
        }
    }

    private boolean validateStatusExists(Long statusId, WorkflowRule rule) {
        IssueStatus status = statusMapper.selectById(statusId);
        if (status == null) {
            log.warn("[RuleEngine] set_field status_id: status {} does not exist, rule '{}' (id={})",
                    statusId, rule.getName(), rule.getId());
            return false;
        }
        return true;
    }

    private boolean validateSprint(Long sprintId, Long projectId, WorkflowRule rule) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) {
            log.warn("[RuleEngine] set_field sprint_id: sprint {} does not exist, rule '{}' (id={})",
                    sprintId, rule.getName(), rule.getId());
            return false;
        }
        if (!Objects.equals(sprint.getProjectId(), projectId)) {
            log.warn("[RuleEngine] set_field sprint_id: sprint {} belongs to project {}, not {}, rule '{}' (id={})",
                    sprintId, sprint.getProjectId(), projectId, rule.getName(), rule.getId());
            return false;
        }
        return true;
    }

    private LocalDate parseDateSafe(String value, WorkflowRule rule) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        if (value.matches("[+-]\\d+d")) {
            try {
                int days = Integer.parseInt(value.substring(0, value.length() - 1));
                return LocalDate.now().plusDays(days);
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] set_field due_date: cannot parse relative date '{}' in rule '{}' (id={})",
                        value, rule.getName(), rule.getId());
                return null;
            }
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("[RuleEngine] set_field due_date: invalid date format '{}' in rule '{}' (id={})",
                    value, rule.getName(), rule.getId());
            return null;
        }
    }
}

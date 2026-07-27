package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuleEngine {

    private final WorkflowRuleMapper ruleMapper;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueTagRelationMapper tagRelationMapper;
    private final IssueStatusMapper statusMapper;
    private final SysUserMapper sysUserMapper;
    private final SprintMapper sprintMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** Valid priority values recognized by the system. */
    private static final Set<String> VALID_PRIORITIES = Set.of(
            "Critical", "High", "Normal", "Low"
    );

    /**
     * 链式规则触发深度防护：每次进入 fireOnFieldChanged 时递增，退出时递减。
     * 当深度超过 MAX_CHAIN_DEPTH 时跳过规则触发，防止链式循环造成无限递归。
     */
    private static final int MAX_CHAIN_DEPTH = 5;
    private static final ThreadLocal<Integer> CHAIN_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * 触发 on-create 规则。
     * <p>
     * 从 DB 重新加载 issue 实体（确保获取最新已提交的状态），
     * 每条规则在独立上下文中执行，单条失败不影响其他规则。
     */
    @Transactional(rollbackFor = Exception.class)
    public void fireOnCreate(Long issueId, Long projectId) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(projectId, "issue_created");
        if (rules.isEmpty()) return;

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            log.warn("[RuleEngine] on-create: issue {} 不存在或已删除，跳过规则执行", issueId);
            return;
        }

        evaluateAndExecute(rules, issue);
    }

    /**
     * 触发 on-field-changed 规则。
     * <p>
     * 从 DB 重新加载 issue 实体，过滤匹配 changedField 的规则后执行。
     * oldValue 传递给条件评估，支持 old_value_equals 等操作符。
     * <p>
     * 内置链式深度防护：超过 MAX_CHAIN_DEPTH 层时记录 WARN 并跳过，防止无限循环。
     */
    @Transactional(rollbackFor = Exception.class)
    public void fireOnFieldChanged(Long issueId, Long projectId, String changedField, String oldValue) {
        int depth = CHAIN_DEPTH.get();
        if (depth >= MAX_CHAIN_DEPTH) {
            log.warn("[RuleEngine] on-field-changed: 链式规则深度已达上限 {}，跳过触发。issueId={}, field={}",
                    MAX_CHAIN_DEPTH, issueId, changedField);
            return;
        }
        CHAIN_DEPTH.set(depth + 1);
        try {
            List<WorkflowRule> rules = ruleMapper.findEnabledRules(projectId, "field_changed");
            if (rules.isEmpty()) return;

            Issue issue = issueMapper.selectById(issueId);
            if (issue == null || issue.getDeletedAt() != null) {
                log.warn("[RuleEngine] on-field-changed: issue {} 不存在或已删除，跳过规则执行", issueId);
                return;
            }

            List<WorkflowRule> matching = rules.stream()
                    .filter(r -> r.getTriggerField() == null || r.getTriggerField().equals(changedField))
                    .toList();
            if (matching.isEmpty()) return;

            evaluateAndExecute(matching, issue, changedField, oldValue);
        } finally {
            CHAIN_DEPTH.set(depth);
        }
    }

    /**
     * 兼容旧接口：直接传入 Issue 对象触发 on-create 规则。
     * 仅供 {@link ScheduledRuleService} 等内部调度场景使用（已在独立事务中，不存在共享引用问题）。
     */
    public void fireOnCreate(Issue issue) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(issue.getProjectId(), "issue_created");
        if (rules.isEmpty()) return;
        evaluateAndExecute(rules, issue);
    }

    /**
     * 兼容旧接口：直接传入 Issue 对象触发 on-field-changed 规则。
     * 仅供内部调度场景使用。
     */
    public void fireOnFieldChanged(Issue issue, String changedField, String oldValue) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(issue.getProjectId(), "field_changed");
        if (rules.isEmpty()) return;
        List<WorkflowRule> matching = rules.stream()
                .filter(r -> r.getTriggerField() == null || r.getTriggerField().equals(changedField))
                .toList();
        if (matching.isEmpty()) return;
        evaluateAndExecute(matching, issue, changedField, oldValue);
    }

    private void evaluateAndExecute(List<WorkflowRule> rules, Issue issue) {
        evaluateAndExecute(rules, issue, null, null);
    }

    private void evaluateAndExecute(List<WorkflowRule> rules, Issue issue, String changedField, String oldValue) {
        for (WorkflowRule rule : rules) {
            try {
                if (evaluateConditions(rule, issue, changedField, oldValue)) {
                    executeActions(rule, issue);
                }
            } catch (Exception e) {
                log.warn("[RuleEngine] Rule '{}' (id={}) failed for issue {}: {}",
                        rule.getName(), rule.getId(), issue.getId(), e.getMessage());
            }
        }
    }

    private boolean evaluateConditions(WorkflowRule rule, Issue issue, String changedField, String oldValue) {
        String json = rule.getConditionJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return true;
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return true;
            for (JsonNode cond : arr) {
                if (!evalCondition(cond, issue, changedField, oldValue)) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[RuleEngine] Condition parse error for rule '{}': {}", rule.getName(), e.getMessage());
            return false;
        }
    }

    private boolean evalCondition(JsonNode cond, Issue issue, String changedField, String oldValue) {
        String field = textOf(cond, "field");
        String op = textOf(cond, "operator");
        String expected = textOf(cond, "value");
        if (field == null || op == null) return true;

        // old_value_* 操作符：对变更字段的旧值进行评估
        if (op.startsWith("old_value_")) {
            // 仅当条件中的 field 与实际变更的字段匹配时，才能使用 oldValue
            String resolvedOldValue;
            if (field.equals(changedField) || field.equals(triggerFieldAlias(changedField))) {
                resolvedOldValue = oldValue;
            } else {
                // 条件针对的字段与当前触发字段不同，oldValue 不适用——跳过此条件（视为通过）
                return true;
            }
            if ("old_value_equals".equals(op)) return Objects.equals(resolvedOldValue, expected);
            if ("old_value_not_equals".equals(op)) return !Objects.equals(resolvedOldValue, expected);
            if ("old_value_in".equals(op)) {
                if (resolvedOldValue == null || expected == null) return false;
                return new HashSet<>(Arrays.asList(expected.split(","))).contains(resolvedOldValue);
            }
            if ("old_value_is_empty".equals(op)) return resolvedOldValue == null || resolvedOldValue.isBlank();
            if ("old_value_is_not_empty".equals(op)) return resolvedOldValue != null && !resolvedOldValue.isBlank();
            return true;
        }

        // 标准操作符：对 issue 当前字段值进行评估
        String actual = fieldValue(issue, field);
        if ("equals".equals(op)) return Objects.equals(actual, expected);
        if ("not_equals".equals(op)) return !Objects.equals(actual, expected);
        if ("contains".equals(op)) return actual != null && expected != null && actual.toLowerCase().contains(expected.toLowerCase());
        if ("in".equals(op)) {
            if (actual == null || expected == null) return false;
            return new HashSet<>(Arrays.asList(expected.split(","))).contains(actual);
        }
        if ("is_empty".equals(op)) return actual == null || actual.isBlank();
        if ("is_not_empty".equals(op)) return actual != null && !actual.isBlank();
        return true;
    }

    /**
     * 将触发字段名映射到可能的别名，以支持条件中使用不同命名风格。
     * 例如触发 "assignee" 但条件中写 "assignee_id" 也应匹配。
     */
    private String triggerFieldAlias(String triggerField) {
        if (triggerField == null) return null;
        return switch (triggerField) {
            case "assignee" -> "assignee_id";
            case "assignee_id" -> "assignee";
            case "sprint" -> "sprint_id";
            case "sprint_id" -> "sprint";
            case "status" -> "status_id";
            case "status_id" -> "status";
            case "type" -> "issue_type";
            case "issue_type" -> "type";
            default -> null;
        };
    }

    private String fieldValue(Issue issue, String field) {
        if ("type".equals(field) || "issue_type".equals(field)) return issue.getIssueType();
        if ("priority".equals(field)) return issue.getPriority();
        if ("status".equals(field) || "status_id".equals(field)) return issue.getStatusId() != null ? String.valueOf(issue.getStatusId()) : null;
        if ("assignee".equals(field) || "assignee_id".equals(field)) return issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null;
        if ("reporter".equals(field) || "reporter_id".equals(field)) return issue.getReporterId() != null ? String.valueOf(issue.getReporterId()) : null;
        if ("sprint".equals(field) || "sprint_id".equals(field)) return issue.getSprintId() != null ? String.valueOf(issue.getSprintId()) : null;
        if ("due_date".equals(field) || "dueDate".equals(field)) return issue.getDueDate() != null ? issue.getDueDate().toString() : null;
        if ("title".equals(field)) return issue.getTitle();
        return null;
    }

    private void executeActions(WorkflowRule rule, Issue issue) {
        String json = rule.getActionJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return;
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return;
            boolean modified = false;
            for (JsonNode act : arr) {
                String type = textOf(act, "type");
                if ("set_field".equals(type)) modified |= doSetField(act, issue, rule);
                else if ("add_tag".equals(type)) doAddTag(act, issue, rule);
                else if ("add_comment".equals(type)) doAddComment(act, issue, rule);
            }
            if (modified) issueMapper.updateById(issue);
        } catch (Exception e) {
            log.warn("[RuleEngine] Action exec error for rule '{}': {}", rule.getName(), e.getMessage());
        }
    }

    /**
     * 供 ScheduledRuleService 调用——对单个工单执行规则的所有动作。
     * 与内部 executeActions 逻辑相同，但为 public 暴露。
     */
    public void executeActionsForSchedule(WorkflowRule rule, Issue issue) {
        executeActions(rule, issue);
    }

    private boolean doSetField(JsonNode act, Issue issue, WorkflowRule rule) {
        String field = textOf(act, "field");
        String value = textOf(act, "value");
        if (field == null) return false;
        String old = fieldValue(issue, field);

        switch (field) {
            case "priority" -> {
                if (!validatePriority(value, rule)) return false;
                issue.setPriority(value);
            }
            case "assignee", "assignee_id" -> {
                Long assigneeId = resolveAndValidateAssignee(value, issue.getProjectId(), rule);
                if (assigneeId == null && value != null && !value.isBlank()) {
                    // value was non-empty but resolved to null (invalid) — skip
                    return false;
                }
                issue.setAssigneeId(assigneeId);
            }
            case "type", "issue_type" -> {
                issue.setIssueType(value);
            }
            case "status", "status_id" -> {
                Long statusId = parseIdSafe(value, "status_id", rule);
                if (statusId == null && value != null && !value.isBlank()) return false;
                if (statusId != null && !validateStatusExists(statusId, rule)) return false;
                issue.setStatusId(statusId);
            }
            case "sprint", "sprint_id" -> {
                Long sprintId = parseIdSafe(value, "sprint_id", rule);
                if (sprintId == null && value != null && !value.isBlank()) return false;
                if (sprintId != null && !validateSprint(sprintId, issue.getProjectId(), rule)) return false;
                issue.setSprintId(sprintId);
            }
            case "due_date", "dueDate" -> {
                LocalDate dueDate = parseDateSafe(value, rule);
                if (dueDate == null && value != null && !value.isBlank()) return false;
                issue.setDueDate(dueDate);
            }
            default -> {
                log.warn("[RuleEngine] set_field: unsupported field '{}' in rule '{}' (id={})",
                        field, rule.getName(), rule.getId());
                return false;
            }
        }
        logActivity(issue.getId(), rule, "updated", field, old, value);
        // 发布字段变更事件，支持链式规则触发（如规则 A 改状态 → 触发监听状态变更的规则 B）
        // 使用规范化的字段名发布事件，与 IssueService 保持一致
        String canonicalField = canonicalFieldName(field);
        eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(
                issue.getId(), issue.getProjectId(), canonicalField, old));
        return true;
    }

    /**
     * 将字段别名统一为规范化字段名，与 IssueService 发布事件时使用的字段名保持一致。
     */
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

    // ===== Validation helpers for doSetField =====

    /**
     * Validate priority value against known enum values.
     * Null/blank is allowed (clears priority).
     */
    private boolean validatePriority(String value, WorkflowRule rule) {
        if (value == null || value.isBlank()) return true;
        if (!VALID_PRIORITIES.contains(value)) {
            log.warn("[RuleEngine] set_field priority: invalid value '{}' in rule '{}' (id={}). " +
                    "Valid values: {}", value, rule.getName(), rule.getId(), VALID_PRIORITIES);
            return false;
        }
        return true;
    }

    /**
     * Resolve assignee value (numeric ID or username) and validate:
     * 1. User exists and is not disabled
     * 2. User is a member of the issue's project
     *
     * Returns null if value is null/blank (unassign), or null if validation fails.
     */
    private Long resolveAndValidateAssignee(String value, Long projectId, WorkflowRule rule) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;

        // Try parsing as numeric ID first, then fallback to username lookup
        Long userId = null;
        try {
            userId = Long.parseLong(value);
        } catch (NumberFormatException e) {
            // Not numeric — try username lookup
            LambdaQueryWrapper<SysUser> uw = new LambdaQueryWrapper<>();
            uw.eq(SysUser::getUsername, value);
            SysUser user = sysUserMapper.selectOne(uw);
            if (user != null) {
                userId = user.getId();
            }
        }

        if (userId == null) {
            log.warn("[RuleEngine] set_field assignee: cannot resolve user '{}' in rule '{}' (id={})",
                    value, rule.getName(), rule.getId());
            return null;
        }

        // Validate user exists and is active
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

        // Validate project membership
        if (!projectService.isProjectMember(userId, projectId)) {
            log.warn("[RuleEngine] set_field assignee: user '{}' (id={}) is not a member of project {}, rule '{}' (id={})",
                    user.getUsername(), userId, projectId, rule.getName(), rule.getId());
            return null;
        }

        return userId;
    }

    /**
     * Safely parse a string value as Long ID. Returns null if blank or parse fails.
     */
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

    /**
     * Validate that the given status ID exists in issue_status table.
     * Note: Rule engine is system-level automation and bypasses user workflow transition constraints,
     * but the target status must at least exist.
     */
    private boolean validateStatusExists(Long statusId, WorkflowRule rule) {
        IssueStatus status = statusMapper.selectById(statusId);
        if (status == null) {
            log.warn("[RuleEngine] set_field status_id: status {} does not exist, rule '{}' (id={})",
                    statusId, rule.getName(), rule.getId());
            return false;
        }
        return true;
    }

    /**
     * Validate sprint: must exist and belong to the same project as the issue.
     */
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

    /**
     * Safely parse a date value. Supports:
     * - ISO format: "2026-07-19"
     * - Relative format: "+7d" (today + 7 days), "-3d" (today - 3 days)
     * - Null/blank: returns null (clear due date)
     */
    private LocalDate parseDateSafe(String value, WorkflowRule rule) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        // Relative date: "+7d", "-3d"
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
        // Absolute date
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("[RuleEngine] set_field due_date: invalid date format '{}' in rule '{}' (id={})",
                    value, rule.getName(), rule.getId());
            return null;
        }
    }

    private void doAddTag(JsonNode act, Issue issue, WorkflowRule rule) {
        String tagIdStr = textOf(act, "tagId");
        if (tagIdStr == null || tagIdStr.isBlank()) return;
        try {
            Long tagId = Long.parseLong(tagIdStr);
            Long cnt = tagRelationMapper.selectCount(new LambdaQueryWrapper<IssueTagRelation>()
                    .eq(IssueTagRelation::getIssueId, issue.getId())
                    .eq(IssueTagRelation::getTagId, tagId));
            if (cnt > 0) return;
            IssueTagRelation rel = new IssueTagRelation();
            rel.setIssueId(issue.getId());
            rel.setTagId(tagId);
            rel.setCreatedAt(LocalDateTime.now());
            tagRelationMapper.insert(rel);
            logActivity(issue.getId(), rule, "tag_added", "tag", null, tagIdStr);
        } catch (NumberFormatException ignored) {}
    }

    private void doAddComment(JsonNode act, Issue issue, WorkflowRule rule) {
        String content = textOf(act, "content");
        if (content == null || content.isBlank()) return;
        content = content.replace("{{rule_name}}", rule.getName())
                .replace("{{issue_key}}", issue.getIssueKey() != null ? issue.getIssueKey() : "")
                .replace("{{issue_title}}", issue.getTitle() != null ? issue.getTitle() : "");
        IssueComment c = new IssueComment();
        c.setIssueId(issue.getId());
        c.setUserId(rule.getCreatedBy());
        c.setContent(content);
        c.setSource("automation");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        logActivity(issue.getId(), rule, "commented", null, null, null);
    }

    private void logActivity(Long issueId, WorkflowRule rule, String action, String field, String oldVal, String newVal) {
        IssueActivity a = new IssueActivity();
        a.setIssueId(issueId);
        // 自动化规则操作不归属具体用户，userId = null
        // 使用规则创建者 ID 会造成活动流显示该用户名，产生误导（REQ-630）
        a.setUserId(null);
        a.setAction(action);
        a.setFieldName(field);
        a.setOldValue(oldVal);
        a.setNewValue(newVal);
        // 对 ID 引用字段设置 displayValue，确保前端展示人类可读文本
        if ("assignee".equals(field) || "assignee_id".equals(field)) {
            a.setOldDisplayValue(resolveUserDisplayName(oldVal));
            a.setNewDisplayValue(resolveUserDisplayName(newVal));
        }
        a.setDetail("{\"source\":\"automation\",\"ruleId\":" + rule.getId() + ",\"ruleName\":\"" + rule.getName().replace("\"", "\\\"") + "\"}");
        // 设置来源标识，前端活动流可据此展示特殊样式（⚡ 自动规则）
        a.setSource("automation");
        a.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(a);
    }

    /**
     * 将用户 ID 字符串解析为用户显示名。
     * 返回 null 如果 idStr 为 null 或无法解析。
     */
    private String resolveUserDisplayName(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null;
        }
        try {
            Long userId = Long.valueOf(idStr);
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getDisplayName() : idStr;
        } catch (NumberFormatException e) {
            // 值已经是用户名而非 ID
            return idStr;
        }
    }

    private String textOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }
}

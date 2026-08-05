package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.integration.service.EmailSendService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueLink;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.entity.IssueVote;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.issue.mapper.IssueVoteMapper;
import com.trackflow.issue.service.IssueService;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.mapper.TimeEntryMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.entity.WorkflowRuleExecutionLog;
import com.trackflow.workflow.entity.WorkflowInitialStatus;
import com.trackflow.workflow.mapper.WorkflowInitialStatusMapper;
import com.trackflow.workflow.mapper.WorkflowRuleExecutionLogMapper;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private final IssueLinkMapper issueLinkMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueVoteMapper voteMapper;
    private final SysUserMapper sysUserMapper;
    private final SprintMapper sprintMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final NotificationMapper notificationMapper;
    private final ProjectService projectService;
    private final IssueService issueService;
    private final EmailSendService emailSendService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final WorkflowInitialStatusMapper initialStatusMapper;
    private final WorkflowRuleExecutionLogMapper executionLogMapper;
    private final TimeEntryMapper timeEntryMapper;
    private final com.trackflow.common.rule.RuleConditionEvaluator ruleConditionEvaluator;

    /** Valid priority values recognized by the system. */
    private static final Set<String> VALID_PRIORITIES = Set.of(
            "Critical", "High", "Normal", "Low"
    );

    /**
     * 链式规则触发深度防护：使用 Redis INCR+TTL 跨线程计数。
     * <p>
     * 由于 WorkflowRuleEventListener 使用 @Async，每次链式触发在新线程中执行，
     * 原 ThreadLocal 方案会导致每条线程的计数都从 0 开始，无法防止跨线程循环。
     * 改用 Redis key 实现跨线程、跨进程的深度计数，key 格式：wf:chain:{issueId}:{changedField}
     */
    private static final int MAX_CHAIN_DEPTH = 5;
    private static final String CHAIN_DEPTH_KEY_PREFIX = "wf:chain:";
    private static final Duration CHAIN_DEPTH_TTL = Duration.ofSeconds(10);

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
     * 内置链式深度防护：使用 Redis INCR+TTL 跨线程计数，超过 MAX_CHAIN_DEPTH 层时记录 WARN 并跳过，
     * 防止 @Async 新线程下 ThreadLocal 失效导致的无限循环。
     */
    @Transactional(rollbackFor = Exception.class)
    public void fireOnFieldChanged(Long issueId, Long projectId, String changedField, String oldValue) {
        String depthKey = CHAIN_DEPTH_KEY_PREFIX + issueId + ":" + changedField;
        Long depth = redisTemplate.opsForValue().increment(depthKey);
        if (depth == null) {
            // Redis 不可用时，降级为允许执行（避免规则系统完全不工作）
            log.warn("[RuleEngine] Redis 不可用，链式深度计数降级跳过，issueId={}, field={}", issueId, changedField);
            depth = 1L;
        }
        if (depth == 1L) {
            // 第一次设置 TTL，防止异常情况下 key 永不过期
            redisTemplate.expire(depthKey, CHAIN_DEPTH_TTL);
        }
        if (depth > MAX_CHAIN_DEPTH) {
            log.warn("[RuleEngine] on-field-changed: 链式规则深度已达上限 {}，跳过触发。issueId={}, field={}, depth={}",
                    MAX_CHAIN_DEPTH, issueId, changedField, depth);
            redisTemplate.opsForValue().decrement(depthKey);
            return;
        }
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
            redisTemplate.opsForValue().decrement(depthKey);
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

    /**
     * 触发 comment_added 规则。
     * <p>
     * 工单下有新评论添加时触发，条件中可用 keyword_contains 检查评论内容是否包含特定关键词。
     */
    @Transactional(rollbackFor = Exception.class)
    public void fireOnCommentAdded(Long issueId, Long projectId, String commentContent) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(projectId, "comment_added");
        if (rules.isEmpty()) return;

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            log.warn("[RuleEngine] on-comment-added: issue {} 不存在或已删除，跳过规则执行", issueId);
            return;
        }

        for (WorkflowRule rule : rules) {
            try {
                if (evaluateConditionsWithComment(rule, issue, commentContent)) {
                    executeActions(rule, issue);
                }
            } catch (Exception e) {
                log.warn("[RuleEngine] Rule '{}' (id={}) failed for issue {} on comment_added: {}",
                        rule.getName(), rule.getId(), issue.getId(), e.getMessage());
            }
        }
    }

    /**
     * 通用事件触发方法 — 用于附件、链接、工时、解决/未解决等简单事件。
     * <p>
     * 这些事件不需要额外上下文参数（如 changedField、commentContent），
     * 仅通过 triggerEvent 字符串匹配规则并执行。
     *
     * @param issueId      工单 ID
     * @param projectId    项目 ID
     * @param triggerEvent 触发事件标识（如 attachment_added, link_added, issue_resolved 等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void fireOnEvent(Long issueId, Long projectId, String triggerEvent) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(projectId, triggerEvent);
        if (rules.isEmpty()) return;

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            log.warn("[RuleEngine] on-{}: issue {} 不存在或已删除，跳过规则执行", triggerEvent, issueId);
            return;
        }

        evaluateAndExecute(rules, issue);
    }

    /**
     * 执行 Action Rule（用户触发的命令规则）。
     * <p>
     * 用户在 Apply Command 弹窗中输入命令名，系统通过 actionCommand 字段匹配对应的规则执行。
     *
     * @param issueId     工单 ID
     * @param projectId   项目 ID
     * @param command     用户输入的命令名
     * @param triggeredBy 触发者用户 ID
     * @return true 如果有规则被触发执行
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean fireActionRule(Long issueId, Long projectId, String command, Long triggeredBy) {
        WorkflowRule rule = ruleMapper.findEnabledActionRule(command, projectId);
        if (rule == null) {
            log.debug("[RuleEngine] action-rule: no enabled rule found for command '{}' in project {}", command, projectId);
            return false;
        }

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) {
            log.warn("[RuleEngine] action-rule: issue {} 不存在或已删除", issueId);
            return false;
        }

        // Action Rule 也要评估 Prerequisites（Guard 条件）
        if (!evaluateConditions(rule, issue, null, null)) {
            log.debug("[RuleEngine] action-rule: guard conditions not met for command '{}' on issue {}",
                    command, issue.getId());
            return false;
        }

        executeActions(rule, issue);
        return true;
    }

    /**
     * 查询用户可用的 Action Rule 命令列表（Guard 条件通过的规则）。
     */
    public List<WorkflowRule> getAvailableActionRules(Long issueId, Long projectId) {
        List<WorkflowRule> actionRules = ruleMapper.findEnabledActionRules(projectId);
        if (actionRules.isEmpty()) return List.of();

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || issue.getDeletedAt() != null) return List.of();

        return actionRules.stream()
                .filter(rule -> evaluateConditions(rule, issue, null, null))
                .toList();
    }

    private boolean evaluateConditionsWithComment(WorkflowRule rule, Issue issue, String commentContent) {
        var context = com.trackflow.common.rule.EvaluationContext.commentAdded(commentContent);
        return ruleConditionEvaluator.evaluate(rule.getConditionJson(), issue, context);
    }

    private void evaluateAndExecute(List<WorkflowRule> rules, Issue issue) {
        evaluateAndExecute(rules, issue, null, null);
    }

    private void evaluateAndExecute(List<WorkflowRule> rules, Issue issue, String changedField, String oldValue) {
        for (WorkflowRule rule : rules) {
            long startTime = System.currentTimeMillis();
            try {
                if (evaluateConditions(rule, issue, changedField, oldValue)) {
                    executeActions(rule, issue);
                    recordExecutionLog(rule, issue, (int)(System.currentTimeMillis() - startTime), true, null);
                } else {
                    // Condition not met — record as skipped (matched=0)
                    recordExecutionLog(rule, issue, (int)(System.currentTimeMillis() - startTime), false, null);
                }
            } catch (Exception e) {
                log.warn("[RuleEngine] Rule '{}' (id={}) failed for issue {}: {}",
                        rule.getName(), rule.getId(), issue.getId(), e.getMessage());
                recordExecutionLog(rule, issue, (int)(System.currentTimeMillis() - startTime), false, e.getMessage());
            }
        }
    }

    /**
     * 记录 on-change 规则执行日志。
     */
    private void recordExecutionLog(WorkflowRule rule, Issue issue, int durationMs, boolean success, String errorMessage) {
        try {
            WorkflowRuleExecutionLog logEntry = new WorkflowRuleExecutionLog();
            logEntry.setRuleId(rule.getId());
            logEntry.setExecutedAt(LocalDateTime.now());
            logEntry.setMatchedCount(1);
            logEntry.setSuccessCount(success ? 1 : 0);
            logEntry.setFailureCount(errorMessage != null ? 1 : 0);
            logEntry.setErrorMessage(errorMessage);
            logEntry.setDurationMs(durationMs);
            logEntry.setIssueKey(issue.getIssueKey());
            executionLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[RuleEngine] 记录执行日志失败: ruleId={}, issueKey={}, error={}",
                    rule.getId(), issue.getIssueKey(), e.getMessage());
        }
    }

    private boolean evaluateConditions(WorkflowRule rule, Issue issue, String changedField, String oldValue) {
        var context = com.trackflow.common.rule.EvaluationContext.fieldChanged(changedField, oldValue);
        return ruleConditionEvaluator.evaluate(rule.getConditionJson(), issue, context);
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
        if ("description".equals(field)) return issue.getDescription();
        return null;
    }

    private void executeActions(WorkflowRule rule, Issue issue) {
        String json = rule.getActionJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return;
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return;
            boolean modified = false;
            // Track created issues for inter-block references (e.g., link_issue referencing create_issue result)
            List<Issue> createdIssues = new java.util.ArrayList<>();
            for (JsonNode act : arr) {
                String type = textOf(act, "type");
                if ("set_field".equals(type)) modified |= doSetField(act, issue, rule);
                else if ("add_tag".equals(type)) doAddTag(act, issue, rule);
                else if ("remove_tag".equals(type)) doRemoveTag(act, issue, rule);
                else if ("add_comment".equals(type)) doAddComment(act, issue, rule);
                else if ("create_issue".equals(type)) {
                    Issue created = doCreateIssue(act, issue, rule);
                    if (created != null) createdIssues.add(created);
                }
                else if ("link_issue".equals(type)) doLinkIssue(act, issue, rule, createdIssues);
                else if ("require_field".equals(type)) {
                    if (!doRequireField(act, issue, rule)) {
                        log.info("[RuleEngine] require_field blocked remaining actions in rule '{}' (id={})",
                                rule.getName(), rule.getId());
                        break; // 阻断后续动作
                    }
                }
                else if ("send_email".equals(type)) doSendEmail(act, issue, rule);
                else if ("show_alert".equals(type)) doShowAlert(act, issue, rule);
                else if ("update_summary".equals(type)) modified |= doUpdateSummary(act, issue, rule);
                else if ("update_description".equals(type)) modified |= doUpdateDescription(act, issue, rule);
                else if ("copy_issue".equals(type)) {
                    Issue copied = doCopyIssue(act, issue, rule);
                    if (copied != null) createdIssues.add(copied);
                }
                else if ("move_to_project".equals(type)) doMoveToProject(act, issue, rule);
                else if ("add_work_item".equals(type)) doAddWorkItem(act, issue, rule);
                else if ("add_vote".equals(type)) doAddVote(act, issue, rule);
                else if ("remove_vote".equals(type)) doRemoveVote(act, issue, rule);
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
        content = interpolateVariables(content, issue, rule);
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

    /**
     * Action: remove_tag — 从工单移除指定标签。
     * <p>
     * JSON 格式示例:
     * {"type": "remove_tag", "tagId": "12345"}
     * <p>
     * 幂等操作：如果标签不存在则静默跳过。
     */
    private void doRemoveTag(JsonNode act, Issue issue, WorkflowRule rule) {
        String tagIdStr = textOf(act, "tagId");
        if (tagIdStr == null || tagIdStr.isBlank()) return;
        try {
            Long tagId = Long.parseLong(tagIdStr);
            int deleted = tagRelationMapper.delete(new LambdaQueryWrapper<IssueTagRelation>()
                    .eq(IssueTagRelation::getIssueId, issue.getId())
                    .eq(IssueTagRelation::getTagId, tagId));
            if (deleted > 0) {
                logActivity(issue.getId(), rule, "tag_removed", "tag", tagIdStr, null);
                log.info("[RuleEngine] remove_tag: removed tag {} from issue {} by rule '{}' (id={})",
                        tagId, issue.getIssueKey(), rule.getName(), rule.getId());
            }
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] remove_tag: invalid tagId '{}' in rule '{}' (id={})",
                    tagIdStr, rule.getName(), rule.getId());
        }
    }

    /**
     * Action: require_field — 检查指定字段是否已填写，否则阻断后续动作执行。
     * <p>
     * JSON 格式示例:
     * {"type": "require_field", "field": "assignee", "errorMessage": "请先分配负责人"}
     * <p>
     * 注意：由于规则引擎在事务提交后异步执行，此动作无法阻断用户原始操作，
     * 而是阻断本规则中后续动作的执行，并记录审计日志。
     *
     * @return true = 字段已填写，继续执行；false = 字段为空，阻断后续动作
     */
    private boolean doRequireField(JsonNode act, Issue issue, WorkflowRule rule) {
        String field = textOf(act, "field");
        if (field == null || field.isBlank()) {
            log.warn("[RuleEngine] require_field: field is not specified in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return true; // 配置错误不阻断
        }
        String actual = fieldValue(issue, field);
        if (actual == null || actual.isBlank()) {
            String errorMessage = textOf(act, "errorMessage");
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "字段 '" + field + "' 为空，规则动作被阻断";
            }
            errorMessage = interpolateVariables(errorMessage, issue, rule);
            logActivity(issue.getId(), rule, "action_blocked", field, null, errorMessage);
            log.info("[RuleEngine] require_field: field '{}' is empty on issue {}, blocking actions in rule '{}' (id={})",
                    field, issue.getIssueKey(), rule.getName(), rule.getId());

            // 发送站内通知给规则创建者，告知阻断发生
            createAlertNotification(rule.getCreatedBy(), issue, rule,
                    "规则阻断: " + errorMessage, "error");
            return false;
        }
        return true;
    }

    /**
     * Action: send_email — 向指定用户发送邮件通知。
     * <p>
     * JSON 格式示例:
     * {"type": "send_email", "target": "reporter", "subject": "[TrackFlow] {issue.key} 状态变更", "body": "工单已解决"}
     * <p>
     * target 支持：
     * - "reporter" — 工单报告人
     * - "assignee" — 当前负责人
     * - "creator" — 规则创建者
     * - 具体邮箱地址（包含 @）
     */
    private void doSendEmail(JsonNode act, Issue issue, WorkflowRule rule) {
        String target = textOf(act, "target");
        String subject = textOf(act, "subject");
        String body = textOf(act, "body");

        if (target == null || target.isBlank()) {
            log.warn("[RuleEngine] send_email: target not specified in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return;
        }
        if (subject == null || subject.isBlank()) {
            subject = "[TrackFlow] 工单 " + issue.getIssueKey() + " 规则通知";
        }
        if (body == null || body.isBlank()) {
            body = "规则 '" + rule.getName() + "' 触发了邮件通知。";
        }

        subject = interpolateVariables(subject, issue, rule);
        body = interpolateVariables(body, issue, rule);

        String toAddress = resolveEmailTarget(target, issue, rule);
        if (toAddress == null || toAddress.isBlank()) {
            log.warn("[RuleEngine] send_email: cannot resolve email for target '{}' in rule '{}' (id={})",
                    target, rule.getName(), rule.getId());
            return;
        }

        // 检查邮件服务是否可用
        if (!emailSendService.isEmailAvailable()) {
            log.warn("[RuleEngine] send_email: email service not available, skipping in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return;
        }

        // 构建 HTML 邮件内容
        String htmlBody = buildRuleEmailHtml(subject, body, issue, rule);
        emailSendService.sendNotificationEmail(toAddress, subject, htmlBody);
        logActivity(issue.getId(), rule, "email_sent", null, null, toAddress);
        log.info("[RuleEngine] send_email: sent to {} for issue {} by rule '{}' (id={})",
                toAddress, issue.getIssueKey(), rule.getName(), rule.getId());
    }

    /**
     * Action: show_alert — 创建站内通知提示。
     * <p>
     * JSON 格式示例:
     * {"type": "show_alert", "style": "acknowledgment", "message": "工单已自动分配"}
     * <p>
     * style 支持: "acknowledgment"（普通提示）和 "error"（错误提示）
     * 通过创建站内通知实现，前端通知面板实时展示。
     */
    private void doShowAlert(JsonNode act, Issue issue, WorkflowRule rule) {
        String style = textOf(act, "style");
        if (style == null || style.isBlank()) style = "acknowledgment";
        String message = textOf(act, "message");
        if (message == null || message.isBlank()) {
            message = "规则 '" + rule.getName() + "' 已执行";
        }
        message = interpolateVariables(message, issue, rule);

        // 通知目标：规则创建者（管理员）
        String targetStr = textOf(act, "target");
        Long targetUserId = resolveAlertTarget(targetStr, issue, rule);

        createAlertNotification(targetUserId, issue, rule, message, style);
        logActivity(issue.getId(), rule, "alert_shown", null, null, style + ": " + message);
        log.info("[RuleEngine] show_alert: '{}' for issue {} by rule '{}' (id={})",
                message, issue.getIssueKey(), rule.getName(), rule.getId());
    }

    /**
     * Action: update_summary — 修改工单标题（支持变量插值）。
     * <p>
     * JSON 格式示例:
     * {"type": "update_summary", "value": "[BUG] {issue.summary}"}
     */
    private boolean doUpdateSummary(JsonNode act, Issue issue, WorkflowRule rule) {
        String value = textOf(act, "value");
        if (value == null || value.isBlank()) {
            log.warn("[RuleEngine] update_summary: value is empty in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return false;
        }
        value = interpolateVariables(value, issue, rule);
        String oldTitle = issue.getTitle();
        if (Objects.equals(oldTitle, value)) return false;

        issue.setTitle(value);
        logActivity(issue.getId(), rule, "updated", "title", oldTitle, value);
        log.info("[RuleEngine] update_summary: changed title on issue {} by rule '{}' (id={})",
                issue.getIssueKey(), rule.getName(), rule.getId());
        return true;
    }

    /**
     * Action: update_description — 修改工单描述（支持变量插值）。
     * <p>
     * JSON 格式示例:
     * {"type": "update_description", "value": "自动生成描述: {issue.type} - {issue.priority}"}
     */
    private boolean doUpdateDescription(JsonNode act, Issue issue, WorkflowRule rule) {
        String value = textOf(act, "value");
        if (value == null) {
            log.warn("[RuleEngine] update_description: value is null in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return false;
        }
        value = interpolateVariables(value, issue, rule);
        String oldDesc = issue.getDescription();
        if (Objects.equals(oldDesc, value)) return false;

        issue.setDescription(value);
        logActivity(issue.getId(), rule, "updated", "description", oldDesc, value);
        log.info("[RuleEngine] update_description: changed description on issue {} by rule '{}' (id={})",
                issue.getIssueKey(), rule.getName(), rule.getId());
        return true;
    }

    // ===== Helper methods for new actions =====

    /**
     * 解析邮件目标地址。
     * 支持: "reporter", "assignee", "creator", 或直接的邮箱地址。
     */
    private String resolveEmailTarget(String target, Issue issue, WorkflowRule rule) {
        if (target.contains("@")) {
            // 直接是邮箱地址
            return target;
        }
        Long userId = switch (target.toLowerCase()) {
            case "reporter" -> issue.getReporterId();
            case "assignee" -> issue.getAssigneeId();
            case "creator" -> rule.getCreatedBy();
            default -> {
                // 尝试作为用户名解析
                LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
                qw.eq(SysUser::getUsername, target);
                SysUser user = sysUserMapper.selectOne(qw);
                yield user != null ? user.getId() : null;
            }
        };
        if (userId == null) return null;
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getEmail() : null;
    }

    /**
     * 解析 show_alert 的目标用户。
     * 支持: "reporter", "assignee", "creator"(默认), 或数字 ID。
     */
    private Long resolveAlertTarget(String target, Issue issue, WorkflowRule rule) {
        if (target == null || target.isBlank() || "creator".equals(target)) {
            return rule.getCreatedBy();
        }
        return switch (target.toLowerCase()) {
            case "reporter" -> issue.getReporterId() != null ? issue.getReporterId() : rule.getCreatedBy();
            case "assignee" -> issue.getAssigneeId() != null ? issue.getAssigneeId() : rule.getCreatedBy();
            default -> {
                try {
                    yield Long.parseLong(target);
                } catch (NumberFormatException e) {
                    yield rule.getCreatedBy();
                }
            }
        };
    }

    /**
     * 创建站内通知（用于 show_alert 和 require_field 阻断通知）。
     */
    private void createAlertNotification(Long userId, Issue issue, WorkflowRule rule, String message, String style) {
        if (userId == null) return;
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setActorId(rule.getCreatedBy());
        notification.setProjectId(issue.getProjectId());
        notification.setTitle("⚡ " + rule.getName());
        notification.setContent(message);
        notification.setType("workflow_alert_" + style);
        notification.setResourceType("issue");
        notification.setResourceId(issue.getId());
        notification.setResourceUrl("/issues/" + issue.getIssueKey());
        notification.setReason("automation");
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        notification.setAggregationCount(1);
        notification.setMailSent(true); // alert 不需要额外发邮件
        notificationMapper.insert(notification);
    }

    /**
     * 构建规则动作邮件的 HTML 内容。
     */
    private String buildRuleEmailHtml(String subject, String body, Issue issue, WorkflowRule rule) {
        return """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #1f2328; margin: 0 0 16px 0;">%s</h2>
                  <div style="color: #57606a; font-size: 14px; line-height: 1.6; margin-bottom: 16px;">
                    %s
                  </div>
                  <div style="background: #f6f8fa; border-radius: 6px; padding: 12px 16px; margin-bottom: 16px;">
                    <p style="margin: 0; font-size: 13px; color: #57606a;">
                      <strong>工单:</strong> %s - %s<br/>
                      <strong>触发规则:</strong> %s
                    </p>
                  </div>
                  <hr style="border: none; border-top: 1px solid #d1d9e0; margin: 24px 0;" />
                  <p style="color: #8b949e; font-size: 12px;">
                    此邮件由 TrackFlow 工作流规则自动发送。
                  </p>
                </div>
                """.formatted(
                escapeHtml(subject),
                escapeHtml(body).replace("\n", "<br/>"),
                escapeHtml(issue.getIssueKey() != null ? issue.getIssueKey() : ""),
                escapeHtml(issue.getTitle() != null ? issue.getTitle() : ""),
                escapeHtml(rule.getName())
        );
    }

    /**
     * 简易 HTML 转义（防止 XSS）。
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Action: create_issue — 创建新工单。
     * <p>
     * JSON 格式示例:
     * {"type": "create_issue", "summary": "Task: {issue.summary}", "issueType": "Task",
     *  "priority": "Normal", "projectId": "same"(默认同项目)}
     *
     * @return 创建的 Issue 对象（用于后续 link_issue 引用），失败返回 null
     */
    private Issue doCreateIssue(JsonNode act, Issue triggerIssue, WorkflowRule rule) {
        String summary = textOf(act, "summary");
        if (summary == null || summary.isBlank()) {
            log.warn("[RuleEngine] create_issue: summary is empty in rule '{}' (id={})", rule.getName(), rule.getId());
            return null;
        }

        summary = interpolateVariables(summary, triggerIssue, rule);
        String description = textOf(act, "description");
        if (description != null) {
            description = interpolateVariables(description, triggerIssue, rule);
        }

        String issueType = textOf(act, "issueType");
        if (issueType == null || issueType.isBlank()) issueType = "Task";
        String priority = textOf(act, "priority");
        if (priority == null || priority.isBlank()) priority = "Normal";

        // Project: default to same project as trigger issue
        Long projectId = triggerIssue.getProjectId();
        String projectIdStr = textOf(act, "projectId");
        if (projectIdStr != null && !"same".equals(projectIdStr)) {
            try {
                projectId = Long.parseLong(projectIdStr);
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] create_issue: invalid projectId '{}' in rule '{}'", projectIdStr, rule.getName());
            }
        }

        // Generate issue key
        com.trackflow.project.entity.Project project = projectService.getById(projectId);
        if (project == null) {
            log.warn("[RuleEngine] create_issue: project {} not found in rule '{}'", projectId, rule.getName());
            return null;
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

        // Resolve initial status for this issue type in this project
        Long initialStatusId = resolveInitialStatus(projectId, issueType);
        newIssue.setStatusId(initialStatusId);

        // Copy sprint from trigger if in same project
        if (Objects.equals(projectId, triggerIssue.getProjectId()) && triggerIssue.getSprintId() != null) {
            newIssue.setSprintId(triggerIssue.getSprintId());
        }

        // Set parent if specified
        String parentRef = textOf(act, "parent");
        if ("trigger".equals(parentRef)) {
            newIssue.setParentId(triggerIssue.getId());
        }

        issueMapper.insert(newIssue);

        logActivity(newIssue.getId(), rule, "created", null, null, null);
        log.info("[RuleEngine] create_issue: created {} (type={}, project={}) by rule '{}' (id={})",
                issueKey, issueType, projectId, rule.getName(), rule.getId());

        // Trigger on-create rules for the new issue (chain effect)
        eventPublisher.publishEvent(new WorkflowRuleEvent.IssueCreated(newIssue.getId(), newIssue.getProjectId()));

        return newIssue;
    }

    /**
     * Action: link_issue — 创建工单关联。
     * <p>
     * JSON 格式示例:
     * {"type": "link_issue", "target": "from_block:0", "linkType": "subtask_of"}
     * target 支持: "from_block:N"（引用本规则第 N 个 create_issue 的结果）、数字 ID（直接引用工单 ID）
     */
    private void doLinkIssue(JsonNode act, Issue triggerIssue, WorkflowRule rule, List<Issue> createdIssues) {
        String linkType = textOf(act, "linkType");
        if (linkType == null || linkType.isBlank()) linkType = "relates_to";

        String targetRef = textOf(act, "target");
        if (targetRef == null || targetRef.isBlank()) {
            log.warn("[RuleEngine] link_issue: target is empty in rule '{}' (id={})", rule.getName(), rule.getId());
            return;
        }

        Long targetIssueId;
        if (targetRef.startsWith("from_block:")) {
            // Reference a previously created issue in this rule execution
            try {
                int blockIndex = Integer.parseInt(targetRef.substring("from_block:".length()));
                if (blockIndex < 0 || blockIndex >= createdIssues.size()) {
                    log.warn("[RuleEngine] link_issue: block index {} out of range (created {} issues) in rule '{}'",
                            blockIndex, createdIssues.size(), rule.getName());
                    return;
                }
                targetIssueId = createdIssues.get(blockIndex).getId();
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] link_issue: invalid block reference '{}' in rule '{}'", targetRef, rule.getName());
                return;
            }
        } else {
            try {
                targetIssueId = Long.parseLong(targetRef);
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] link_issue: invalid target ID '{}' in rule '{}'", targetRef, rule.getName());
                return;
            }
        }

        // Determine source and target based on link direction
        Long sourceIssueId = triggerIssue.getId();

        // Check if link already exists
        Long existingCount = issueLinkMapper.selectCount(new LambdaQueryWrapper<IssueLink>()
                .eq(IssueLink::getSourceIssueId, sourceIssueId)
                .eq(IssueLink::getTargetIssueId, targetIssueId)
                .eq(IssueLink::getLinkType, linkType));
        if (existingCount > 0) {
            log.debug("[RuleEngine] link_issue: link already exists between {} and {} (type={})",
                    sourceIssueId, targetIssueId, linkType);
            return;
        }

        IssueLink link = new IssueLink();
        link.setSourceIssueId(sourceIssueId);
        link.setTargetIssueId(targetIssueId);
        link.setLinkType(linkType);
        link.setCreatedBy(rule.getCreatedBy());
        link.setCreatedAt(LocalDateTime.now());
        issueLinkMapper.insert(link);

        logActivity(triggerIssue.getId(), rule, "link_added", "link", null,
                linkType + " → " + targetIssueId);
        log.info("[RuleEngine] link_issue: created link {} -> {} (type={}) by rule '{}' (id={})",
                sourceIssueId, targetIssueId, linkType, rule.getName(), rule.getId());
    }

    /**
     * 解析初始状态 ID：优先从 workflow_initial_status 配置查询，fallback 到第一个非关闭状态。
     */
    private Long resolveInitialStatus(Long projectId, String issueType) {
        // 优先从工作流初始状态配置中查找（按 project+issueType 优先级）
        String effectiveIssueType = (issueType == null || issueType.isBlank()) ? "*" : issueType;

        // 1. 精确项目 + 精确类型
        if (projectId != null && !"*".equals(effectiveIssueType)) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .eq(WorkflowInitialStatus::getProjectId, projectId)
                            .eq(WorkflowInitialStatus::getIssueType, effectiveIssueType));
            if (config != null) return config.getStatusId();
        }

        // 2. 精确项目 + 通配类型
        if (projectId != null) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .eq(WorkflowInitialStatus::getProjectId, projectId)
                            .eq(WorkflowInitialStatus::getIssueType, "*"));
            if (config != null) return config.getStatusId();
        }

        // 3. 全局 + 精确类型
        if (!"*".equals(effectiveIssueType)) {
            WorkflowInitialStatus config = initialStatusMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInitialStatus>()
                            .isNull(WorkflowInitialStatus::getProjectId)
                            .eq(WorkflowInitialStatus::getIssueType, effectiveIssueType));
            if (config != null) return config.getStatusId();
        }

        // 4. 全局 + 通配类型
        WorkflowInitialStatus config = initialStatusMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInitialStatus>()
                        .isNull(WorkflowInitialStatus::getProjectId)
                        .eq(WorkflowInitialStatus::getIssueType, "*"));
        if (config != null) return config.getStatusId();

        // 5. Fallback：取第一个非关闭状态
        IssueStatus status = statusMapper.selectOne(new LambdaQueryWrapper<IssueStatus>()
                .eq(IssueStatus::getIsClosed, false)
                .orderByAsc(IssueStatus::getSortOrder)
                .last("LIMIT 1"));
        return status != null ? status.getId() : null;
    }

    /**
     * 变量插值 — 将文本中的占位符替换为实际值。
     * <p>
     * 支持格式：
     * - {issue.id} / {issue.summary} / {issue.key} / {issue.project.name}
     * - {{rule_name}} / {{issue_key}} / {{issue_title}} (旧格式兼容)
     */
    private String interpolateVariables(String text, Issue issue, WorkflowRule rule) {
        if (text == null) return null;
        // 旧格式兼容
        text = text.replace("{{rule_name}}", rule.getName())
                .replace("{{issue_key}}", issue.getIssueKey() != null ? issue.getIssueKey() : "")
                .replace("{{issue_title}}", issue.getTitle() != null ? issue.getTitle() : "");
        // 新格式：{issue.*}
        text = text.replace("{issue.id}", issue.getIssueKey() != null ? issue.getIssueKey() : String.valueOf(issue.getId()))
                .replace("{issue.summary}", issue.getTitle() != null ? issue.getTitle() : "")
                .replace("{issue.key}", issue.getIssueKey() != null ? issue.getIssueKey() : "")
                .replace("{issue.title}", issue.getTitle() != null ? issue.getTitle() : "")
                .replace("{issue.type}", issue.getIssueType() != null ? issue.getIssueType() : "")
                .replace("{issue.priority}", issue.getPriority() != null ? issue.getPriority() : "");
        // {issue.project.name} — resolve project name
        if (text.contains("{issue.project.name}")) {
            com.trackflow.project.entity.Project proj = projectService.getById(issue.getProjectId());
            String projectName = proj != null ? proj.getName() : "";
            text = text.replace("{issue.project.name}", projectName);
        }
        return text;
    }

    private void logActivity(Long issueId, WorkflowRule rule, String action, String field, String oldVal, String newVal) {
        IssueActivity a = new IssueActivity();
        a.setIssueId(issueId);
        // issue_activity.user_id 为 NOT NULL，使用规则创建者作为操作者
        // 前端通过 source="automation" 字段来区分是人工操作还是自动规则触发
        a.setUserId(rule.getCreatedBy());
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
        if (child == null || child.isNull()) {
            // Fallback: look inside "params" sub-object (action blocks may nest params)
            JsonNode params = node.get("params");
            if (params != null && params.isObject()) {
                child = params.get(key);
            }
        }
        if (child == null || child.isNull()) return null;
        return child.asText();
    }

    // ========== 高级动作：copy_issue / move_to_project / add_work_item / add_vote / remove_vote ==========

    /**
     * 克隆工单到同项目或其他项目。
     * 复制字段：标题（可加前缀）、描述、类型、优先级。
     * 状态重置为目标项目初始状态；附件可选复制。
     */
    private Issue doCopyIssue(JsonNode act, Issue source, WorkflowRule rule) {
        // 解析目标项目
        Long targetProjectId = resolveProjectId(textOf(act, "targetProjectId"), source);
        if (targetProjectId == null) {
            log.warn("[RuleEngine] copy_issue: targetProjectId could not be resolved in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return null;
        }

        com.trackflow.project.entity.Project targetProject = projectService.getById(targetProjectId);
        if (targetProject == null) {
            log.warn("[RuleEngine] copy_issue: target project {} not found in rule '{}' (id={})",
                    targetProjectId, rule.getName(), rule.getId());
            return null;
        }

        // 构建标题
        String summaryPrefix = textOf(act, "summaryPrefix");
        if (summaryPrefix == null) summaryPrefix = "";
        summaryPrefix = interpolateVariables(summaryPrefix, source, rule);
        String title = summaryPrefix + (source.getTitle() != null ? source.getTitle() : "");

        // 生成 issue key
        int seq = projectService.nextIssueSequence(targetProjectId);
        String issueKey = targetProject.getKey() + "-" + seq;

        Issue copy = new Issue();
        copy.setProjectId(targetProjectId);
        copy.setIssueKey(issueKey);
        copy.setTitle(title);
        copy.setDescription(source.getDescription());
        copy.setIssueType(source.getIssueType());
        copy.setPriority(source.getPriority());
        copy.setReporterId(rule.getCreatedBy());
        copy.setCreatedBy(rule.getCreatedBy());

        // 状态重置为目标项目初始状态
        Long initialStatusId = resolveInitialStatus(targetProjectId, source.getIssueType());
        copy.setStatusId(initialStatusId);

        // Sprint 可选复制（仅同项目有效）
        boolean copySprint = boolOf(act, "copySprint") != null && Boolean.TRUE.equals(boolOf(act, "copySprint"));
        if (copySprint && Objects.equals(targetProjectId, source.getProjectId()) && source.getSprintId() != null) {
            copy.setSprintId(source.getSprintId());
        }

        issueMapper.insert(copy);

        // 附件复制
        boolean copyAttachments = boolOf(act, "copyAttachments") != null && Boolean.TRUE.equals(boolOf(act, "copyAttachments"));
        if (copyAttachments) {
            List<IssueAttachment> attachments = attachmentMapper.selectList(
                    new LambdaQueryWrapper<IssueAttachment>()
                            .eq(IssueAttachment::getIssueId, source.getId()));
            for (IssueAttachment att : attachments) {
                IssueAttachment newAtt = new IssueAttachment();
                newAtt.setIssueId(copy.getId());
                newAtt.setFileName(att.getFileName());
                newAtt.setFilePath(att.getFilePath()); // 共享同一存储路径
                newAtt.setFileSize(att.getFileSize());
                newAtt.setContentType(att.getContentType());
                newAtt.setUploadedBy(rule.getCreatedBy());
                newAtt.setCreatedAt(LocalDateTime.now());
                attachmentMapper.insert(newAtt);
            }
        }

        logActivity(copy.getId(), rule, "created", null, null, null);
        log.info("[RuleEngine] copy_issue: cloned {} → {} (project={}) by rule '{}' (id={})",
                source.getIssueKey(), issueKey, targetProjectId, rule.getName(), rule.getId());

        // 触发 on-create 规则
        eventPublisher.publishEvent(new WorkflowRuleEvent.IssueCreated(copy.getId(), copy.getProjectId()));

        return copy;
    }

    /**
     * 将工单移动到另一个项目。
     * 委托给 IssueService 的自动化移动方法（跳过权限检查）。
     */
    private void doMoveToProject(JsonNode act, Issue issue, WorkflowRule rule) {
        String targetProjectIdStr = textOf(act, "targetProjectId");
        if (targetProjectIdStr == null || targetProjectIdStr.isBlank()) {
            log.warn("[RuleEngine] move_to_project: targetProjectId is empty in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return;
        }

        Long targetProjectId;
        try {
            targetProjectId = Long.parseLong(targetProjectIdStr);
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] move_to_project: invalid targetProjectId '{}' in rule '{}' (id={})",
                    targetProjectIdStr, rule.getName(), rule.getId());
            return;
        }

        try {
            issueService.moveToProjectByAutomation(issue.getId(), targetProjectId, rule.getCreatedBy());
            log.info("[RuleEngine] move_to_project: moved issue {} to project {} by rule '{}' (id={})",
                    issue.getIssueKey(), targetProjectId, rule.getName(), rule.getId());
        } catch (Exception e) {
            log.warn("[RuleEngine] move_to_project: failed for issue {} in rule '{}': {}",
                    issue.getIssueKey(), rule.getName(), e.getMessage());
        }
    }

    /**
     * 为工单自动添加工时记录。
     */
    private void doAddWorkItem(JsonNode act, Issue issue, WorkflowRule rule) {
        String durationStr = textOf(act, "duration");
        if (durationStr == null || durationStr.isBlank()) {
            log.warn("[RuleEngine] add_work_item: duration is empty in rule '{}' (id={})",
                    rule.getName(), rule.getId());
            return;
        }

        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(durationStr);
        } catch (NumberFormatException e) {
            log.warn("[RuleEngine] add_work_item: invalid duration '{}' in rule '{}' (id={})",
                    durationStr, rule.getName(), rule.getId());
            return;
        }
        if (durationMinutes <= 0 || durationMinutes > 1440) {
            log.warn("[RuleEngine] add_work_item: duration {} out of range [1,1440] in rule '{}' (id={})",
                    durationMinutes, rule.getName(), rule.getId());
            return;
        }

        String description = textOf(act, "description");
        if (description != null) {
            description = interpolateVariables(description, issue, rule);
        }

        // 解析工作日期
        String dateStr = textOf(act, "date");
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
    }

    /**
     * 为工单添加投票（以规则创建者身份）。
     */
    private void doAddVote(JsonNode act, Issue issue, WorkflowRule rule) {
        Long userId = rule.getCreatedBy();
        // 幂等：已投票则跳过
        if (voteMapper.isVoted(issue.getId(), userId)) {
            log.debug("[RuleEngine] add_vote: user {} already voted on issue {}, skipping",
                    userId, issue.getIssueKey());
            return;
        }

        IssueVote vote = new IssueVote();
        vote.setIssueId(issue.getId());
        vote.setUserId(userId);
        vote.setCreatedAt(LocalDateTime.now());
        voteMapper.insert(vote);
        voteMapper.refreshVoteCount(issue.getId());

        logActivity(issue.getId(), rule, "voted", null, null, null);
        log.info("[RuleEngine] add_vote: user {} voted on issue {} by rule '{}' (id={})",
                userId, issue.getIssueKey(), rule.getName(), rule.getId());
    }

    /**
     * 移除工单投票（以规则创建者身份）。
     */
    private void doRemoveVote(JsonNode act, Issue issue, WorkflowRule rule) {
        Long userId = rule.getCreatedBy();
        // 幂等：未投票则跳过
        if (!voteMapper.isVoted(issue.getId(), userId)) {
            log.debug("[RuleEngine] remove_vote: user {} has no vote on issue {}, skipping",
                    userId, issue.getIssueKey());
            return;
        }

        voteMapper.delete(new LambdaQueryWrapper<IssueVote>()
                .eq(IssueVote::getIssueId, issue.getId())
                .eq(IssueVote::getUserId, userId));
        voteMapper.refreshVoteCount(issue.getId());

        logActivity(issue.getId(), rule, "unvoted", null, null, null);
        log.info("[RuleEngine] remove_vote: user {} unvoted on issue {} by rule '{}' (id={})",
                userId, issue.getIssueKey(), rule.getName(), rule.getId());
    }

    /**
     * 解析 JSON 节点中的布尔值字段。
     */
    private Boolean boolOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            JsonNode params = node.get("params");
            if (params != null && params.isObject()) {
                child = params.get(key);
            }
        }
        if (child == null || child.isNull()) return null;
        if (child.isBoolean()) return child.asBoolean();
        // 支持字符串 "true"/"false"
        String text = child.asText();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        return null;
    }

    /**
     * 解析项目 ID：支持 "same"（同项目）或数字 ID 字符串。
     */
    private Long resolveProjectId(String projectIdStr, Issue source) {
        if (projectIdStr == null || "same".equals(projectIdStr) || projectIdStr.isBlank()) {
            return source.getProjectId();
        }
        try {
            return Long.parseLong(projectIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuleEngine {

    private final WorkflowRuleMapper ruleMapper;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueTagRelationMapper tagRelationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 触发 on-create 规则。
     * <p>
     * 从 DB 重新加载 issue 实体（确保获取最新已提交的状态），
     * 每条规则在独立上下文中执行，单条失败不影响其他规则。
     */
    @Transactional
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
     */
    @Transactional
    public void fireOnFieldChanged(Long issueId, Long projectId, String changedField, String oldValue) {
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
        if ("priority".equals(field)) issue.setPriority(value);
        else if ("assignee".equals(field) || "assignee_id".equals(field)) {
            issue.setAssigneeId(value != null && !value.isBlank() ? Long.parseLong(value) : null);
        } else if ("type".equals(field) || "issue_type".equals(field)) issue.setIssueType(value);
        else return false;
        logActivity(issue.getId(), rule, "updated", field, old, value);
        return true;
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
        a.setUserId(rule.getCreatedBy());
        a.setAction(action);
        a.setFieldName(field);
        a.setOldValue(oldVal);
        a.setNewValue(newVal);
        a.setDetail("{\"source\":\"automation\",\"ruleId\":" + rule.getId() + ",\"ruleName\":\"" + rule.getName().replace("\"", "\\\"") + "\"}");
        a.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(a);
    }

    private String textOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }
}

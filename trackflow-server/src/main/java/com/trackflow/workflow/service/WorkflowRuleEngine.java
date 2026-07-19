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
    private final ObjectMapper objectMapper;

    public void fireOnCreate(Issue issue) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(issue.getProjectId(), "issue_created");
        if (rules.isEmpty()) return;
        evaluateAndExecute(rules, issue);
    }

    public void fireOnFieldChanged(Issue issue, String changedField, String oldValue) {
        List<WorkflowRule> rules = ruleMapper.findEnabledRules(issue.getProjectId(), "field_changed");
        if (rules.isEmpty()) return;
        List<WorkflowRule> matching = rules.stream()
                .filter(r -> r.getTriggerField() == null || r.getTriggerField().equals(changedField))
                .toList();
        if (matching.isEmpty()) return;
        evaluateAndExecute(matching, issue);
    }

    private void evaluateAndExecute(List<WorkflowRule> rules, Issue issue) {
        for (WorkflowRule rule : rules) {
            try {
                if (evaluateConditions(rule, issue)) {
                    executeActions(rule, issue);
                }
            } catch (Exception e) {
                log.warn("[RuleEngine] Rule '{}' failed: {}", rule.getName(), e.getMessage());
            }
        }
    }

    private boolean evaluateConditions(WorkflowRule rule, Issue issue) {
        String json = rule.getConditionJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return true;
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return true;
            for (JsonNode cond : arr) {
                if (!evalCondition(cond, issue)) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[RuleEngine] Condition parse error: {}", e.getMessage());
            return false;
        }
    }

    private boolean evalCondition(JsonNode cond, Issue issue) {
        String field = textOf(cond, "field");
        String op = textOf(cond, "operator");
        String expected = textOf(cond, "value");
        if (field == null || op == null) return true;
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
            log.warn("[RuleEngine] Action exec error: {}", e.getMessage());
        }
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

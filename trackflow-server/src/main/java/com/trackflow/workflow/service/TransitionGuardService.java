package com.trackflow.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.issue.entity.Issue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 工作流转换守卫条件评估服务。
 * <p>
 * 评估 workflow_transition.conditions JSONB 字段中定义的前置条件，
 * 决定特定转换路径对给定 Issue 是否可用。
 * <p>
 * 条件结构（参考 YouTrack Workflow Guard Conditions）：
 * <pre>
 * {
 *   "conditions": [
 *     {"field": "assignee_id", "operator": "is_not_empty"},
 *     {"field": "priority",    "operator": "equals",        "value": "High"}
 *   ]
 * }
 * </pre>
 * 多个条件之间是 AND 关系（所有条件都满足才允许转换）。
 * <p>
 * 支持的字段：assignee_id, status_id, priority, issue_type, reporter_id, sprint_id, due_date, title
 * <p>
 * 支持的操作符：equals, not_equals, contains, in, is_empty, is_not_empty
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitionGuardService {

    private final ObjectMapper objectMapper;

    /**
     * 评估守卫条件是否满足。
     *
     * @param conditionsJson 守卫条件 JSONB 字符串（来自 workflow_transition.conditions）
     * @param issue          当前工单
     * @return true 表示守卫条件满足（或无条件限制），false 表示条件不满足（此转换不可用）
     */
    public boolean evaluate(String conditionsJson, Issue issue) {
        if (conditionsJson == null || conditionsJson.isBlank()
                || "{}".equals(conditionsJson.trim()) || "null".equals(conditionsJson.trim())) {
            // 空条件：无任何守卫，始终允许
            return true;
        }

        try {
            JsonNode root = objectMapper.readTree(conditionsJson);
            JsonNode conditionsArray = root.get("conditions");
            if (conditionsArray == null || !conditionsArray.isArray() || conditionsArray.isEmpty()) {
                return true;
            }

            // AND 语义：所有条件都必须满足
            for (JsonNode cond : conditionsArray) {
                if (!evalCondition(cond, issue)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("[TransitionGuard] 条件解析失败，默认允许转换: conditionsJson={}, error={}",
                    conditionsJson, e.getMessage());
            return true; // 解析失败降级为允许，避免因配置错误阻塞所有用户
        }
    }

    private boolean evalCondition(JsonNode cond, Issue issue) {
        String field = textOf(cond, "field");
        String op = textOf(cond, "operator");
        String expected = textOf(cond, "value");

        if (field == null || op == null) {
            return true; // 不完整的条件定义，跳过
        }

        String actual = fieldValue(issue, field);

        return switch (op) {
            case "equals" -> Objects.equals(actual, expected);
            case "not_equals" -> !Objects.equals(actual, expected);
            case "contains" -> actual != null && expected != null
                    && actual.toLowerCase().contains(expected.toLowerCase());
            case "in" -> {
                if (actual == null || expected == null) yield false;
                yield java.util.Arrays.asList(expected.split(",")).contains(actual);
            }
            case "is_empty" -> actual == null || actual.isBlank();
            case "is_not_empty" -> actual != null && !actual.isBlank();
            default -> {
                log.warn("[TransitionGuard] 未知操作符: {}", op);
                yield true; // 未知操作符降级为允许
            }
        };
    }

    private String fieldValue(Issue issue, String field) {
        return switch (field) {
            case "assignee_id", "assignee" ->
                    issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null;
            case "status_id", "status" ->
                    issue.getStatusId() != null ? String.valueOf(issue.getStatusId()) : null;
            case "priority" -> issue.getPriority();
            case "issue_type", "type" -> issue.getIssueType();
            case "reporter_id", "reporter" ->
                    issue.getReporterId() != null ? String.valueOf(issue.getReporterId()) : null;
            case "sprint_id", "sprint" ->
                    issue.getSprintId() != null ? String.valueOf(issue.getSprintId()) : null;
            case "due_date", "dueDate" ->
                    issue.getDueDate() != null ? issue.getDueDate().toString() : null;
            case "title" -> issue.getTitle();
            case "description" -> issue.getDescription();
            default -> {
                log.debug("[TransitionGuard] 未知字段: {}", field);
                yield null;
            }
        };
    }

    private String textOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }
}

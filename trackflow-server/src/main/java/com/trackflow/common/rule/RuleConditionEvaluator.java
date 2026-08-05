package com.trackflow.common.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueLink;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 公共规则条件评估器 —— 统一处理所有自动化引擎的 JSON 条件求值。
 * <p>
 * 支持：
 * <ul>
 *   <li>逻辑组合：AND / OR / NOT（递归嵌套）</li>
 *   <li>字段比对：equals / not_equals / contains / in / is_empty / is_not_empty</li>
 *   <li>旧值比对：old_value_equals / old_value_not_equals / old_value_in / old_value_is_empty / old_value_is_not_empty</li>
 *   <li>时间条件：overdue / due_within_days</li>
 *   <li>高级条件（conditionType）：issue_resolved / issue_has_tag / issue_attribute_count /
 *       issue_created_within / issue_updated_within / created_by / updated_by /
 *       user_has_role / issue_in_project / keyword_contains</li>
 * </ul>
 * <p>
 * 由 WorkflowRuleEngine 和 ScheduledRuleService 共同使用，消除条件评估逻辑重复。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleConditionEvaluator {

    private final IssueCommentMapper commentMapper;
    private final IssueLinkMapper issueLinkMapper;
    private final IssueTagRelationMapper tagRelationMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueMapper issueMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ObjectMapper objectMapper;

    /**
     * 评估条件 JSON 字符串是否对给定工单成立。
     * <p>
     * 支持完整上下文（字段变更场景、评论触发场景），也支持简化调用（无变更上下文）。
     *
     * @param conditionJson 条件 JSON（可为数组格式或对象格式）
     * @param issue         目标工单
     * @param context       评估上下文（变更字段、旧值、评论内容等）；传 null 表示无上下文
     * @return 条件是否满足
     */
    public boolean evaluate(String conditionJson, Issue issue, EvaluationContext context) {
        if (conditionJson == null || conditionJson.isBlank() || "[]".equals(conditionJson.trim())) {
            return true;
        }
        if (context == null) {
            context = EvaluationContext.EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(conditionJson);
            // 向后兼容：如果是数组，则隐式 AND
            if (root.isArray()) {
                for (JsonNode cond : root) {
                    if (!evalConditionNode(cond, issue, context)) return false;
                }
                return true;
            }
            // 新格式：对象节点，递归求值
            if (root.isObject()) {
                return evalConditionNode(root, issue, context);
            }
            return true;
        } catch (Exception e) {
            log.warn("[RuleConditionEvaluator] Condition parse error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 简化调用：无变更上下文。
     */
    public boolean evaluate(String conditionJson, Issue issue) {
        return evaluate(conditionJson, issue, EvaluationContext.EMPTY);
    }

    /**
     * 评估已解析的 JsonNode 条件树。
     * 适用于条件 JSON 已在调用方解析为 JsonNode 的场景（如 ScheduledRuleService 的分批过滤）。
     *
     * @param conditionRoot 条件 JsonNode（数组或对象格式）
     * @param issue         目标工单
     * @param context       评估上下文
     * @return 条件是否满足
     */
    public boolean evaluate(com.fasterxml.jackson.databind.JsonNode conditionRoot, Issue issue, EvaluationContext context) {
        if (conditionRoot == null || conditionRoot.isNull()) return true;
        if (context == null) {
            context = EvaluationContext.EMPTY;
        }
        if (conditionRoot.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode cond : conditionRoot) {
                if (!evalConditionNode(cond, issue, context)) return false;
            }
            return true;
        }
        if (conditionRoot.isObject()) {
            return evalConditionNode(conditionRoot, issue, context);
        }
        return true;
    }

    // ==================== 递归求值核心 ====================

    /**
     * 递归求值条件节点。
     * 支持逻辑节点（type=and/or/not）和叶子节点（type=condition 或无 type 的旧格式）。
     */
    private boolean evalConditionNode(JsonNode node, Issue issue, EvaluationContext context) {
        String type = textOf(node, "type");
        if (type == null) {
            // 旧格式叶子节点（无 type 字段，直接含 field/operator/value）
            return evalLeafCondition(node, issue, context);
        }
        return switch (type) {
            case "and" -> {
                JsonNode conditions = node.get("conditions");
                if (conditions == null || !conditions.isArray() || conditions.isEmpty()) yield true;
                for (JsonNode child : conditions) {
                    if (!evalConditionNode(child, issue, context)) yield false;
                }
                yield true;
            }
            case "or" -> {
                JsonNode conditions = node.get("conditions");
                if (conditions == null || !conditions.isArray() || conditions.isEmpty()) yield true;
                for (JsonNode child : conditions) {
                    if (evalConditionNode(child, issue, context)) yield true;
                }
                yield false;
            }
            case "not" -> {
                JsonNode condition = node.get("condition");
                if (condition == null) yield true;
                yield !evalConditionNode(condition, issue, context);
            }
            case "condition" -> evalLeafCondition(node, issue, context);
            default -> evalLeafCondition(node, issue, context);
        };
    }

    /**
     * 评估叶子条件节点。
     */
    private boolean evalLeafCondition(JsonNode cond, Issue issue, EvaluationContext context) {
        // 新格式：conditionType 字段区分条件类型
        String conditionType = textOf(cond, "conditionType");
        if (conditionType != null) {
            // keyword_contains 在评论场景需要传递 commentContent
            if ("keyword_contains".equals(conditionType)) {
                return evaluateKeywordContains(cond, issue, context.commentContent());
            }
            return evaluateTypedCondition(conditionType, cond, issue, context);
        }

        String field = textOf(cond, "field");
        String op = textOf(cond, "operator");
        String expected = textOf(cond, "value");

        // 特殊条件：评论关键词匹配（旧格式兼容）
        if (("comment_content".equals(field) || "keyword".equals(field)) && context.commentContent() != null) {
            if ("contains".equals(op) || "keyword_contains".equals(op)) {
                return expected != null && context.commentContent().toLowerCase().contains(expected.toLowerCase());
            }
            return true;
        }

        if (field == null || op == null) return true;

        // old_value_* 操作符：对变更字段的旧值进行评估
        if (op.startsWith("old_value_")) {
            return evaluateOldValueOperator(op, field, expected, context);
        }

        // 标准操作符：对 issue 当前字段值进行评估
        String actual = getFieldValue(issue, field);
        if ("equals".equals(op)) return Objects.equals(actual, expected);
        if ("not_equals".equals(op)) return !Objects.equals(actual, expected);
        if ("contains".equals(op)) return actual != null && expected != null && actual.toLowerCase().contains(expected.toLowerCase());
        if ("in".equals(op)) {
            if (actual == null || expected == null) return false;
            return new HashSet<>(Arrays.asList(expected.split(","))).contains(actual);
        }
        if ("is_empty".equals(op)) return actual == null || actual.isBlank();
        if ("is_not_empty".equals(op)) return actual != null && !actual.isBlank();

        // 时间相关操作符
        if ("overdue".equals(op)) {
            if (issue.getDueDate() == null) return false;
            return issue.getDueDate().isBefore(LocalDate.now());
        }
        if ("due_within_days".equals(op)) {
            if (issue.getDueDate() == null || expected == null) return false;
            try {
                int days = Integer.parseInt(expected);
                LocalDate today = LocalDate.now();
                LocalDate due = issue.getDueDate();
                return !due.isBefore(today) && due.isBefore(today.plusDays(days + 1));
            } catch (NumberFormatException e) {
                log.warn("[RuleConditionEvaluator] due_within_days value not a valid integer: {}", expected);
                return false;
            }
        }
        return true;
    }

    // ==================== old_value 操作符 ====================

    private boolean evaluateOldValueOperator(String op, String field, String expected, EvaluationContext context) {
        String changedField = context.changedField();
        String oldValue = context.oldValue();

        // 仅当条件中的 field 与实际变更的字段匹配时，才能使用 oldValue
        String resolvedOldValue;
        if (field.equals(changedField) || field.equals(triggerFieldAlias(changedField))) {
            resolvedOldValue = oldValue;
        } else {
            // 条件针对的字段与当前触发字段不同，oldValue 不适用——跳过此条件（视为通过）
            return true;
        }

        return switch (op) {
            case "old_value_equals" -> Objects.equals(resolvedOldValue, expected);
            case "old_value_not_equals" -> !Objects.equals(resolvedOldValue, expected);
            case "old_value_in" -> {
                if (resolvedOldValue == null || expected == null) yield false;
                yield new HashSet<>(Arrays.asList(expected.split(","))).contains(resolvedOldValue);
            }
            case "old_value_is_empty" -> resolvedOldValue == null || resolvedOldValue.isBlank();
            case "old_value_is_not_empty" -> resolvedOldValue != null && !resolvedOldValue.isBlank();
            default -> true;
        };
    }

    // ==================== conditionType 分发 ====================

    /**
     * 统一处理 conditionType 分发的条件评估。
     */
    private boolean evaluateTypedCondition(String conditionType, JsonNode cond, Issue issue, EvaluationContext context) {
        return switch (conditionType) {
            case "keyword_contains" -> evaluateKeywordContains(cond, issue, context.commentContent());
            case "issue_resolved" -> {
                if (issue.getStatusId() == null) yield false;
                IssueStatus status = statusMapper.selectById(issue.getStatusId());
                yield status != null && Boolean.TRUE.equals(status.getIsClosed());
            }
            case "issue_has_tag" -> {
                String tagId = textOf(cond, "tagId");
                if (tagId == null || tagId.isBlank()) yield false;
                try {
                    long tid = Long.parseLong(tagId);
                    long count = tagRelationMapper.selectCount(
                            new LambdaQueryWrapper<IssueTagRelation>()
                                    .eq(IssueTagRelation::getIssueId, issue.getId())
                                    .eq(IssueTagRelation::getTagId, tid)
                    );
                    yield count > 0;
                } catch (NumberFormatException e) {
                    log.warn("[RuleConditionEvaluator] issue_has_tag: invalid tagId: {}", tagId);
                    yield false;
                }
            }
            case "issue_attribute_count" -> evaluateAttributeCount(cond, issue);
            case "issue_created_within" -> {
                String days = textOf(cond, "days");
                if (days == null || issue.getCreatedAt() == null) yield false;
                try {
                    int d = Integer.parseInt(days);
                    yield issue.getCreatedAt().isAfter(LocalDateTime.now().minusDays(d));
                } catch (NumberFormatException e) {
                    log.warn("[RuleConditionEvaluator] issue_created_within: invalid days: {}", days);
                    yield false;
                }
            }
            case "issue_updated_within" -> {
                String days = textOf(cond, "days");
                if (days == null || issue.getUpdatedAt() == null) yield false;
                try {
                    int d = Integer.parseInt(days);
                    yield issue.getUpdatedAt().isAfter(LocalDateTime.now().minusDays(d));
                } catch (NumberFormatException e) {
                    log.warn("[RuleConditionEvaluator] issue_updated_within: invalid days: {}", days);
                    yield false;
                }
            }
            case "created_by" -> {
                String userId = textOf(cond, "userId");
                if (userId == null || issue.getCreatedBy() == null) yield false;
                if ("current_user".equals(userId)) {
                    Long currentUserId = SecurityUtils.getCurrentUserId();
                    yield currentUserId != null && currentUserId.equals(issue.getCreatedBy());
                }
                try {
                    yield Long.parseLong(userId) == issue.getCreatedBy();
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "updated_by" -> {
                String userId = textOf(cond, "userId");
                if (userId == null || issue.getUpdatedBy() == null) yield false;
                if ("current_user".equals(userId)) {
                    Long currentUserId = SecurityUtils.getCurrentUserId();
                    yield currentUserId != null && currentUserId.equals(issue.getUpdatedBy());
                }
                try {
                    yield Long.parseLong(userId) == issue.getUpdatedBy();
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "user_has_role" -> {
                if (!context.hasUserContext()) {
                    // On-schedule 场景没有"当前用户"上下文，条件不适用
                    log.debug("[RuleConditionEvaluator] user_has_role: no user context, skipping");
                    yield true;
                }
                String role = textOf(cond, "role");
                if (role == null || role.isBlank()) yield false;
                Long currentUserId = SecurityUtils.getCurrentUserId();
                if (currentUserId == null || issue.getProjectId() == null) yield false;
                List<String> roleCodes = projectMemberMapper.selectRoleCodesByUserAndProject(
                        currentUserId, issue.getProjectId());
                yield roleCodes.contains(role);
            }
            case "issue_in_project" -> {
                String projectId = textOf(cond, "projectId");
                if (projectId == null || issue.getProjectId() == null) yield false;
                try {
                    yield Long.parseLong(projectId) == issue.getProjectId();
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> {
                log.warn("[RuleConditionEvaluator] Unknown conditionType: {}", conditionType);
                yield true; // 未知条件类型降级为通过
            }
        };
    }

    // ==================== attribute_count ====================

    private boolean evaluateAttributeCount(JsonNode cond, Issue issue) {
        String attribute = textOf(cond, "attribute");
        String operator = textOf(cond, "operator");
        String valueStr = textOf(cond, "value");
        if (attribute == null || operator == null || valueStr == null) return false;

        int threshold;
        try {
            threshold = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            log.warn("[RuleConditionEvaluator] issue_attribute_count: invalid value: {}", valueStr);
            return false;
        }

        long actualCount = switch (attribute) {
            case "comments" -> commentMapper.selectCount(
                    new LambdaQueryWrapper<IssueComment>().eq(IssueComment::getIssueId, issue.getId()));
            case "links" -> issueLinkMapper.selectCount(
                    new LambdaQueryWrapper<IssueLink>()
                            .eq(IssueLink::getSourceIssueId, issue.getId())
                            .or()
                            .eq(IssueLink::getTargetIssueId, issue.getId()));
            case "attachments" -> attachmentMapper.selectCount(
                    new LambdaQueryWrapper<IssueAttachment>()
                            .eq(IssueAttachment::getIssueId, issue.getId()));
            default -> {
                log.warn("[RuleConditionEvaluator] issue_attribute_count: unknown attribute: {}", attribute);
                yield -1L;
            }
        };

        if (actualCount < 0) return false;

        return switch (operator) {
            case "greater_than" -> actualCount > threshold;
            case "less_than" -> actualCount < threshold;
            case "equals" -> actualCount == threshold;
            case "greater_than_or_equals" -> actualCount >= threshold;
            case "less_than_or_equals" -> actualCount <= threshold;
            default -> {
                log.warn("[RuleConditionEvaluator] issue_attribute_count: unknown operator: {}", operator);
                yield false;
            }
        };
    }

    // ==================== keyword_contains ====================

    /**
     * 评估 keyword_contains 条件。
     *
     * @param cond           条件节点
     * @param issue          当前工单
     * @param commentContent 评论内容（仅 comment_added 触发器提供，其他场景为 null）
     */
    private boolean evaluateKeywordContains(JsonNode cond, Issue issue, String commentContent) {
        String attribute = textOf(cond, "attribute");
        if (attribute == null || attribute.isBlank()) {
            log.warn("[RuleConditionEvaluator] keyword_contains: missing attribute");
            return false;
        }

        List<String> keywords = parseKeywordList(cond.get("keywords"));
        if (keywords.isEmpty()) {
            log.warn("[RuleConditionEvaluator] keyword_contains: no valid keywords");
            return false;
        }

        return switch (attribute) {
            case "summary" -> containsAnyKeyword(issue.getTitle(), keywords);
            case "description" -> containsAnyKeyword(issue.getDescription(), keywords);
            case "comments" -> containsAnyKeyword(commentContent, keywords);
            case "linked_summaries" -> checkLinkedIssueSummaries(issue.getId(), keywords);
            default -> {
                log.warn("[RuleConditionEvaluator] keyword_contains: unsupported attribute: {}", attribute);
                yield false;
            }
        };
    }

    // ==================== 字段值提取 ====================

    /**
     * 从 Issue 对象中提取字段的字符串值。
     * 支持多种字段别名（如 "assignee" 和 "assignee_id" 等效）。
     */
    public String getFieldValue(Issue issue, String field) {
        if (field == null) return null;
        return switch (field) {
            case "type", "issue_type" -> issue.getIssueType();
            case "priority" -> issue.getPriority();
            case "status", "status_id" -> issue.getStatusId() != null ? String.valueOf(issue.getStatusId()) : null;
            case "assignee", "assignee_id" -> issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null;
            case "reporter", "reporter_id" -> issue.getReporterId() != null ? String.valueOf(issue.getReporterId()) : null;
            case "sprint", "sprint_id" -> issue.getSprintId() != null ? String.valueOf(issue.getSprintId()) : null;
            case "due_date", "dueDate" -> issue.getDueDate() != null ? issue.getDueDate().toString() : null;
            case "title" -> issue.getTitle();
            case "description" -> issue.getDescription();
            default -> null;
        };
    }

    // ==================== 工具方法 ====================

    /**
     * 将触发字段名映射到可能的别名。
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

    private boolean checkLinkedIssueSummaries(Long issueId, List<String> keywords) {
        if (issueId == null) return false;
        List<IssueLink> links = issueLinkMapper.selectList(
                new LambdaQueryWrapper<IssueLink>()
                        .eq(IssueLink::getSourceIssueId, issueId)
                        .or()
                        .eq(IssueLink::getTargetIssueId, issueId)
        );
        if (links.isEmpty()) return false;

        Set<Long> linkedIssueIds = new HashSet<>();
        for (IssueLink link : links) {
            if (link.getSourceIssueId().equals(issueId)) {
                linkedIssueIds.add(link.getTargetIssueId());
            } else {
                linkedIssueIds.add(link.getSourceIssueId());
            }
        }

        List<Issue> linkedIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .in(Issue::getId, linkedIssueIds)
                        .isNull(Issue::getDeletedAt)
                        .select(Issue::getId, Issue::getTitle)
        );

        for (Issue linked : linkedIssues) {
            if (containsAnyKeyword(linked.getTitle(), keywords)) return true;
        }
        return false;
    }

    private List<String> parseKeywordList(JsonNode keywordsNode) {
        if (keywordsNode == null || keywordsNode.isNull()) return List.of();
        if (keywordsNode.isArray()) {
            List<String> result = new java.util.ArrayList<>();
            for (JsonNode kw : keywordsNode) {
                String text = kw.asText().trim();
                if (!text.isEmpty()) result.add(text);
            }
            return result;
        }
        // Fallback：逗号分隔字符串
        String text = keywordsNode.asText();
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return false;
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    private String textOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }
}

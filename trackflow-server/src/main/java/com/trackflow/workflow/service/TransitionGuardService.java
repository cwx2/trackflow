package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueLink;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final IssueLinkMapper issueLinkMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;

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

            // 格式1：{ "conditions": [...] } — 顶层 AND 数组（旧格式）
            JsonNode conditionsArray = root.get("conditions");
            if (conditionsArray != null && conditionsArray.isArray()) {
                for (JsonNode cond : conditionsArray) {
                    if (!evalConditionNode(cond, issue)) return false;
                }
                return true;
            }

            // 格式2：直接数组 [...]（旧格式）
            if (root.isArray()) {
                for (JsonNode cond : root) {
                    if (!evalConditionNode(cond, issue)) return false;
                }
                return true;
            }

            // 格式3：单个逻辑对象 { "type": "and"/"or"/"not", ... }（新格式）
            if (root.isObject() && root.has("type")) {
                return evalConditionNode(root, issue);
            }

            return true;
        } catch (Exception e) {
            log.warn("[TransitionGuard] 条件解析失败，默认允许转换: conditionsJson={}, error={}",
                    conditionsJson, e.getMessage());
            return true; // 解析失败降级为允许，避免因配置错误阻塞所有用户
        }
    }

    /**
     * 获取守卫条件失败的人类可读原因（用于 API 错误消息）。
     * <p>
     * 仅在 evaluate() 返回 false 后调用。
     *
     * @param conditionsJson 守卫条件 JSONB 字符串
     * @param issue          当前工单
     * @return 失败原因描述（中文），如果无法确定则返回通用提示
     */
    public String getFailureReason(String conditionsJson, Issue issue) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return "守卫条件未满足";
        }
        try {
            JsonNode root = objectMapper.readTree(conditionsJson);
            JsonNode conditionsArray = root.get("conditions");
            if (conditionsArray == null || !conditionsArray.isArray()) {
                return "守卫条件未满足";
            }
            for (JsonNode cond : conditionsArray) {
                if (!evalConditionNode(cond, issue)) {
                    return describeFailedCondition(cond, issue);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "守卫条件未满足";
    }

    private String describeFailedCondition(JsonNode cond, Issue issue) {
        String conditionType = textOf(cond, "conditionType");
        if ("links_resolved".equals(conditionType)) {
            String linkType = textOf(cond, "linkType");
            return switch (linkType != null ? linkType : "") {
                case "subtask_of" -> "请先完成所有子任务";
                case "parent_of" -> "请先完成所有子级工单";
                case "blocks" -> "请先解决所有阻塞方工单";
                default -> "请先完成所有关联工单";
            };
        }
        if ("children_resolved".equals(conditionType)) {
            return "请先完成所有子工单";
        }
        // 字段条件
        String field = textOf(cond, "field");
        String op = textOf(cond, "operator");
        if ("is_not_empty".equals(op)) {
            return "字段 " + fieldLabel(field) + " 不能为空";
        }
        if ("is_empty".equals(op)) {
            return "字段 " + fieldLabel(field) + " 必须为空";
        }
        if ("equals".equals(op)) {
            return "字段 " + fieldLabel(field) + " 必须等于 " + textOf(cond, "value");
        }
        return "守卫条件未满足：" + fieldLabel(field) + " " + op;
    }

    private String fieldLabel(String field) {
        if (field == null) return "未知字段";
        return switch (field) {
            case "assignee_id", "assignee" -> "负责人";
            case "priority" -> "优先级";
            case "issue_type", "type" -> "工单类型";
            case "reporter_id", "reporter" -> "报告人";
            case "sprint_id", "sprint" -> "Sprint";
            case "due_date", "dueDate" -> "截止日期";
            case "title" -> "标题";
            case "description" -> "描述";
            default -> field;
        };
    }

    /**
     * 递归求值条件节点，支持 AND/OR/NOT 逻辑组合。
     */
    private boolean evalConditionNode(JsonNode node, Issue issue) {
        String type = textOf(node, "type");
        if (type == null) {
            return evalCondition(node, issue);
        }
        return switch (type) {
            case "and" -> {
                JsonNode conditions = node.get("conditions");
                if (conditions == null || !conditions.isArray() || conditions.isEmpty()) yield true;
                for (JsonNode child : conditions) {
                    if (!evalConditionNode(child, issue)) yield false;
                }
                yield true;
            }
            case "or" -> {
                JsonNode conditions = node.get("conditions");
                if (conditions == null || !conditions.isArray() || conditions.isEmpty()) yield true;
                for (JsonNode child : conditions) {
                    if (evalConditionNode(child, issue)) yield true;
                }
                yield false;
            }
            case "not" -> {
                JsonNode condition = node.get("condition");
                if (condition == null) yield true;
                yield !evalConditionNode(condition, issue);
            }
            case "condition" -> evalCondition(node, issue);
            default -> evalCondition(node, issue);
        };
    }

    private boolean evalCondition(JsonNode cond, Issue issue) {
        // 支持高级 conditionType 条件
        String conditionType = textOf(cond, "conditionType");
        if (conditionType != null) {
            return evalAdvancedCondition(conditionType, cond, issue);
        }

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

    /**
     * 评估高级条件类型（非简单 field/operator 模式）。
     * <p>
     * 支持的 conditionType：
     * <ul>
     *   <li><b>links_resolved</b>：指定关联类型的所有关联工单必须处于已关闭状态。
     *       参数 linkType 支持：subtask_of, parent_of, blocks</li>
     *   <li><b>children_resolved</b>：所有直接子工单（通过 parentId 关联）必须已关闭。
     *       使用 Issue 的派生字段 childCount/childClosedCount 快速判断。</li>
     * </ul>
     */
    private boolean evalAdvancedCondition(String conditionType, JsonNode cond, Issue issue) {
        return switch (conditionType) {
            case "links_resolved" -> evalLinksResolved(cond, issue);
            case "children_resolved" -> evalChildrenResolved(issue);
            default -> {
                log.debug("[TransitionGuard] conditionType={} 未知，降级为允许", conditionType);
                yield true;
            }
        };
    }

    /**
     * links_resolved：检查指定关联类型的所有关联工单是否都已关闭。
     * <p>
     * 条件格式：{"conditionType": "links_resolved", "linkType": "subtask_of"}
     * <p>
     * linkType 语义（基于 issue_link 表的方向性）：
     * <ul>
     *   <li>subtask_of：查找所有以当前工单为 target（即当前工单是父级）的 subtask_of 链接，
     *       检查所有 source（子任务）是否已关闭</li>
     *   <li>parent_of：查找所有以当前工单为 source 的 parent_of 链接，
     *       检查所有 target（子级）是否已关闭</li>
     *   <li>blocks：查找所有以当前工单为 target（即当前工单被阻塞）的 blocks 链接，
     *       检查所有 source（阻塞方）是否已关闭</li>
     * </ul>
     * <p>
     * 如果没有关联工单，条件视为满足（无需阻塞）。
     */
    private boolean evalLinksResolved(JsonNode cond, Issue issue) {
        String linkType = textOf(cond, "linkType");
        if (linkType == null || linkType.isBlank()) {
            log.warn("[TransitionGuard] links_resolved 缺少 linkType 参数，降级为允许");
            return true;
        }

        // 针对 subtask_of 关联，优先使用 Issue 的派生字段（避免额外查询）
        if ("subtask_of".equals(linkType)) {
            return evalChildrenResolved(issue);
        }

        // 确定查询方向
        List<Long> relatedIssueIds;
        if ("parent_of".equals(linkType)) {
            // 当前工单是 source（父级），查找 target（子级）
            List<IssueLink> links = issueLinkMapper.selectList(
                    new LambdaQueryWrapper<IssueLink>()
                            .eq(IssueLink::getSourceIssueId, issue.getId())
                            .eq(IssueLink::getLinkType, linkType)
            );
            relatedIssueIds = links.stream().map(IssueLink::getTargetIssueId).toList();
        } else {
            // blocks: 当前工单是 target（被阻塞方），查找 source（阻塞方）
            // subtask_of: 当前工单是 target（父级），查找 source（子任务）
            List<IssueLink> links = issueLinkMapper.selectList(
                    new LambdaQueryWrapper<IssueLink>()
                            .eq(IssueLink::getTargetIssueId, issue.getId())
                            .eq(IssueLink::getLinkType, linkType)
            );
            relatedIssueIds = links.stream().map(IssueLink::getSourceIssueId).toList();
        }

        if (relatedIssueIds.isEmpty()) {
            return true; // 无关联工单，不阻塞
        }

        // 批量查询关联工单状态
        List<Issue> relatedIssues = issueMapper.selectBatchIds(relatedIssueIds);

        // 获取所有关闭状态 ID
        Set<Long> closedStatusIds = getClosedStatusIds();

        // 检查是否所有关联工单（未删除的）都已关闭
        boolean allResolved = relatedIssues.stream()
                .filter(i -> i.getDeletedAt() == null) // 忽略已删除的
                .allMatch(i -> closedStatusIds.contains(i.getStatusId()));

        if (!allResolved) {
            long unresolvedCount = relatedIssues.stream()
                    .filter(i -> i.getDeletedAt() == null)
                    .filter(i -> !closedStatusIds.contains(i.getStatusId()))
                    .count();
            log.debug("[TransitionGuard] links_resolved 未通过: issueId={}, linkType={}, 未解决数={}",
                    issue.getId(), linkType, unresolvedCount);
        }
        return allResolved;
    }

    /**
     * children_resolved：使用 Issue 的派生字段快速判断所有直接子工单是否已关闭。
     * <p>
     * 利用 issue 表的 child_count 和 child_closed_count 字段，无需额外查询。
     * 如果没有子工单（childCount=0 或 null），条件视为满足。
     */
    private boolean evalChildrenResolved(Issue issue) {
        Integer childCount = issue.getChildCount();
        Integer childClosedCount = issue.getChildClosedCount();

        if (childCount == null || childCount == 0) {
            return true; // 无子工单，不阻塞
        }

        boolean allClosed = childClosedCount != null && childClosedCount >= childCount;
        if (!allClosed) {
            log.debug("[TransitionGuard] children_resolved 未通过: issueId={}, childCount={}, childClosedCount={}",
                    issue.getId(), childCount, childClosedCount);
        }
        return allClosed;
    }

    /**
     * 获取所有已关闭状态的 ID 集合。
     */
    private Set<Long> getClosedStatusIds() {
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>()
                        .eq(IssueStatus::getIsClosed, true)
        );
        return allStatuses.stream()
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());
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

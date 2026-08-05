package com.trackflow.quickaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.*;
import com.trackflow.issue.service.IssueService;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.quickaction.entity.QuickActionDefinition;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Action 规则执行器 —— 执行 execution_actions 配置的自动化动作。
 * <p>
 * 支持的动作类型：
 * - set_field: 设置工单字段值（priority, assignee, issueType, dueDate）
 * - add_tag: 添加标签（按名称匹配或创建）
 * - add_comment: 添加评论
 * - send_notification: 发送通知
 * - set_status: 变更工单状态
 *
 * @since V125
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionRuleExecutor {

    private final IssueMapper issueMapper;
    private final IssueActivityMapper issueActivityMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueTagMapper issueTagMapper;
    private final IssueTagRelationMapper issueTagRelationMapper;
    private final IssueService issueService;
    private final com.trackflow.issue.service.IssueCommentService commentService;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    /**
     * 执行规则的所有自动化动作。
     *
     * @param issue      目标工单
     * @param definition 动作定义
     * @param operatorId 操作者 ID
     * @return 执行结果摘要
     */
    @Transactional
    public ActionRuleResult executeActions(Issue issue, QuickActionDefinition definition, Long operatorId) {
        String executionActionsJson = definition.getExecutionActions();
        if (executionActionsJson == null || executionActionsJson.isBlank()
                || "[]".equals(executionActionsJson)) {
            return ActionRuleResult.noActions();
        }

        List<Map<String, Object>> actions;
        try {
            actions = objectMapper.readValue(executionActionsJson,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("[ActionRuleExecutor] 解析 execution_actions 失败: definitionId={}, json={}",
                    definition.getId(), executionActionsJson, e);
            return ActionRuleResult.error("execution_actions 解析失败: " + e.getMessage());
        }

        List<String> executedActions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> action : actions) {
            String type = String.valueOf(action.getOrDefault("type", ""));
            try {
                switch (type) {
                    case "set_field" -> {
                        executeSetField(issue, action, operatorId);
                        executedActions.add("set_field:" + action.get("field"));
                    }
                    case "add_tag" -> {
                        executeAddTag(issue, action, operatorId);
                        executedActions.add("add_tag:" + action.get("tagName"));
                    }
                    case "add_comment" -> {
                        executeAddComment(issue, action, operatorId, definition.getLabel());
                        executedActions.add("add_comment");
                    }
                    case "send_notification" -> {
                        executeSendNotification(issue, action, operatorId, definition.getLabel());
                        executedActions.add("send_notification");
                    }
                    case "set_status" -> {
                        executeSetStatus(issue, action, operatorId);
                        executedActions.add("set_status:" + action.get("statusName"));
                    }
                    default -> {
                        log.warn("[ActionRuleExecutor] 未知动作类型: {}", type);
                        errors.add("未知动作类型: " + type);
                    }
                }
            } catch (Exception e) {
                log.error("[ActionRuleExecutor] 执行动作失败: type={}, issue={}, error={}",
                        type, issue.getId(), e.getMessage(), e);
                errors.add(type + ": " + e.getMessage());
            }
        }

        // 记录整体活动
        recordActionRuleExecution(issue, definition, operatorId, executedActions);

        return new ActionRuleResult(true, executedActions, errors);
    }

    // ===== 动作执行器 =====

    private void executeSetField(Issue issue, Map<String, Object> action, Long operatorId) {
        String field = String.valueOf(action.getOrDefault("field", ""));
        Object rawValue = action.get("value");
        String value = rawValue != null ? String.valueOf(rawValue) : null;

        switch (field) {
            case "priority" -> {
                String oldValue = issue.getPriority();
                issue.setPriority(value);
                issueMapper.updateById(issue);
                recordFieldChange(issue.getId(), operatorId, "priority", oldValue, value);
            }
            case "assignee", "assignee_id" -> {
                Long oldAssigneeId = issue.getAssigneeId();
                Long newAssigneeId = resolveUserId(value, issue.getProjectId());
                issue.setAssigneeId(newAssigneeId);
                issueMapper.updateById(issue);
                recordFieldChange(issue.getId(), operatorId, "assignee_id",
                        oldAssigneeId != null ? String.valueOf(oldAssigneeId) : null,
                        newAssigneeId != null ? String.valueOf(newAssigneeId) : null);
            }
            case "issueType", "issue_type" -> {
                String oldType = issue.getIssueType();
                issue.setIssueType(value);
                issueMapper.updateById(issue);
                recordFieldChange(issue.getId(), operatorId, "issue_type", oldType, value);
            }
            case "dueDate", "due_date" -> {
                LocalDate oldDue = issue.getDueDate();
                LocalDate newDue = resolveDateValue(value);
                issue.setDueDate(newDue);
                issueMapper.updateById(issue);
                recordFieldChange(issue.getId(), operatorId, "due_date",
                        oldDue != null ? oldDue.toString() : null,
                        newDue != null ? newDue.toString() : null);
            }
            case "sprintId", "sprint_id" -> {
                Long oldSprintId = issue.getSprintId();
                Long newSprintId = value != null && !value.isBlank() ? Long.parseLong(value) : null;
                issue.setSprintId(newSprintId);
                issueMapper.updateById(issue);
                recordFieldChange(issue.getId(), operatorId, "sprint_id",
                        oldSprintId != null ? String.valueOf(oldSprintId) : null,
                        newSprintId != null ? String.valueOf(newSprintId) : null);
            }
            default -> log.warn("[ActionRuleExecutor] 不支持设置的字段: {}", field);
        }
    }

    private void executeAddTag(Issue issue, Map<String, Object> action, Long operatorId) {
        String tagName = String.valueOf(action.getOrDefault("tagName", ""));
        if (tagName.isBlank()) return;

        // 查找项目标签
        LambdaQueryWrapper<IssueTag> tw = new LambdaQueryWrapper<>();
        tw.eq(IssueTag::getProjectId, issue.getProjectId())
                .eq(IssueTag::getName, tagName);
        IssueTag tag = issueTagMapper.selectOne(tw);

        // 如果标签不存在，自动创建
        if (tag == null) {
            tag = new IssueTag();
            tag.setProjectId(issue.getProjectId());
            tag.setName(tagName);
            tag.setColor(String.valueOf(action.getOrDefault("tagColor", "#808080")));
            tag.setCreatedBy(operatorId);
            tag.setCreatedAt(LocalDateTime.now());
            issueTagMapper.insert(tag);
            log.info("[ActionRuleExecutor] 自动创建标签: project={}, name={}",
                    issue.getProjectId(), tagName);
        }

        // 检查是否已存在关联
        LambdaQueryWrapper<IssueTagRelation> rw = new LambdaQueryWrapper<>();
        rw.eq(IssueTagRelation::getIssueId, issue.getId())
                .eq(IssueTagRelation::getTagId, tag.getId());
        if (issueTagRelationMapper.selectCount(rw) > 0) {
            log.debug("[ActionRuleExecutor] 标签已关联, 跳过: issue={}, tag={}",
                    issue.getId(), tagName);
            return;
        }

        // 添加关联
        IssueTagRelation relation = new IssueTagRelation();
        relation.setIssueId(issue.getId());
        relation.setTagId(tag.getId());
        relation.setCreatedAt(LocalDateTime.now());
        issueTagRelationMapper.insert(relation);

        // 记录活动
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issue.getId());
        activity.setUserId(operatorId);
        activity.setAction("tag_added");
        activity.setFieldName("tags");
        activity.setNewValue(tagName);
        activity.setDetail("{\"source\":\"action_rule\"}");
        activity.setCreatedAt(LocalDateTime.now());
        issueActivityMapper.insert(activity);
    }

    private void executeAddComment(Issue issue, Map<String, Object> action,
                                   Long operatorId, String actionLabel) {
        String content = String.valueOf(action.getOrDefault("content", ""));
        if (content.isBlank()) {
            content = "由自动化规则「" + actionLabel + "」执行";
        }
        // Replace template variables
        content = replaceVariables(content, issue, operatorId);
        commentService.addComment(issue.getId(), content);
    }

    private void executeSendNotification(Issue issue, Map<String, Object> action,
                                         Long operatorId, String actionLabel) {
        // Determine recipient
        String recipientType = String.valueOf(action.getOrDefault("recipientType", "assignee"));
        Long recipientId = resolveRecipient(recipientType, issue);
        if (recipientId == null) {
            log.debug("[ActionRuleExecutor] 无法确定通知接收人: type={}", recipientType);
            return;
        }

        String title = String.valueOf(action.getOrDefault("title",
                "工单 " + issue.getIssueKey() + " 触发了规则「" + actionLabel + "」"));
        String content = String.valueOf(action.getOrDefault("content",
                "工单 [" + issue.getIssueKey() + "] " + issue.getTitle() + " 已触发自动化规则。"));
        title = replaceVariables(title, issue, operatorId);
        content = replaceVariables(content, issue, operatorId);

        notificationService.notify(
                recipientId,
                operatorId,
                title,
                content,
                NotificationType.mention,
                NotificationReason.rule_triggered,
                "issue",
                issue.getId(),
                issue.getProjectId()
        );
    }

    private void executeSetStatus(Issue issue, Map<String, Object> action, Long operatorId) {
        String statusName = String.valueOf(action.getOrDefault("statusName", ""));
        if (statusName.isBlank()) return;

        LambdaQueryWrapper<IssueStatus> sw = new LambdaQueryWrapper<>();
        sw.eq(IssueStatus::getName, statusName);
        IssueStatus targetStatus = issueStatusMapper.selectOne(sw);
        if (targetStatus == null) {
            log.warn("[ActionRuleExecutor] 目标状态不存在: {}", statusName);
            return;
        }

        try {
            issueService.transitStatus(issue.getId(), targetStatus.getId(), null);
        } catch (Exception e) {
            log.warn("[ActionRuleExecutor] 状态转换失败: issue={}, target={}, error={}",
                    issue.getId(), statusName, e.getMessage());
        }
    }

    // ===== 辅助方法 =====

    private Long resolveUserId(String value, Long projectId) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        // If numeric, treat as user ID
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {}
        // Try lookup by username
        LambdaQueryWrapper<SysUser> uw = new LambdaQueryWrapper<>();
        uw.eq(SysUser::getUsername, value);
        SysUser user = sysUserMapper.selectOne(uw);
        return user != null ? user.getId() : null;
    }

    private LocalDate resolveDateValue(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        // Support relative dates: "+7d" means today + 7 days
        if (value.startsWith("+") && value.endsWith("d")) {
            try {
                int days = Integer.parseInt(value.substring(1, value.length() - 1));
                return LocalDate.now().plusDays(days);
            } catch (NumberFormatException ignored) {}
        }
        // Absolute date
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("[ActionRuleExecutor] 无法解析日期: {}", value);
            return null;
        }
    }

    private Long resolveRecipient(String type, Issue issue) {
        return switch (type) {
            case "assignee" -> issue.getAssigneeId();
            case "reporter", "created_by" -> issue.getCreatedBy();
            default -> {
                try { yield Long.parseLong(type); }
                catch (NumberFormatException e) { yield null; }
            }
        };
    }

    private String replaceVariables(String template, Issue issue, Long operatorId) {
        if (template == null) return "";
        String result = template
                .replace("{issueKey}", issue.getIssueKey() != null ? issue.getIssueKey() : "")
                .replace("{issueTitle}", issue.getTitle() != null ? issue.getTitle() : "")
                .replace("{issueType}", issue.getIssueType() != null ? issue.getIssueType() : "");

        // Replace {operatorName}
        if (result.contains("{operatorName}")) {
            SysUser user = sysUserMapper.selectById(operatorId);
            result = result.replace("{operatorName}",
                    user != null ? user.getDisplayName() : String.valueOf(operatorId));
        }

        // Replace {projectName}
        if (result.contains("{projectName}")) {
            Project project = projectMapper.selectById(issue.getProjectId());
            result = result.replace("{projectName}",
                    project != null ? project.getName() : "");
        }

        return result;
    }

    private void recordFieldChange(Long issueId, Long userId, String field, String oldVal, String newVal) {
        // Skip no-op changes (value didn't actually change)
        if (Objects.equals(oldVal, newVal)) {
            log.debug("[ActionRuleExecutor] 跳过无变化的字段记录: issue={}, field={}, value={}",
                    issueId, field, oldVal);
            return;
        }
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction("updated");
        activity.setFieldName(field);
        activity.setOldValue(oldVal);
        activity.setNewValue(newVal);
        activity.setDetail("{\"source\":\"action_rule\"}");
        activity.setCreatedAt(LocalDateTime.now());
        issueActivityMapper.insert(activity);
    }

    private void recordActionRuleExecution(Issue issue, QuickActionDefinition definition,
                                           Long operatorId, List<String> executedActions) {
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issue.getId());
        activity.setUserId(operatorId);
        activity.setAction("action_rule_executed");
        activity.setFieldName("action_rule");
        activity.setNewValue(definition.getLabel());
        // Build proper JSON for detail field
        String actionsJson;
        try {
            actionsJson = objectMapper.writeValueAsString(executedActions);
        } catch (Exception e) {
            actionsJson = "[]";
        }
        activity.setDetail(String.format(
                "{\"definitionId\":%d,\"actionKey\":\"%s\",\"executedActions\":%s}",
                definition.getId(), definition.getActionKey(), actionsJson));
        activity.setCreatedAt(LocalDateTime.now());
        issueActivityMapper.insert(activity);
    }

    // ===== 结果类 =====

    public record ActionRuleResult(
            boolean success,
            List<String> executedActions,
            List<String> errors
    ) {
        public static ActionRuleResult noActions() {
            return new ActionRuleResult(true, List.of(), List.of());
        }

        public static ActionRuleResult error(String message) {
            return new ActionRuleResult(false, List.of(), List.of(message));
        }
    }
}

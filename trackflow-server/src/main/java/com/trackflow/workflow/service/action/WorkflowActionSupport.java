package com.trackflow.workflow.service.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

/**
 * 工作流动作执行器基类 — 提供所有执行器共用的辅助方法。
 * <p>
 * 包括 JSON 节点读取、变量插值、活动日志记录、用户解析、通知创建等。
 * 子类通过继承获得这些能力，专注实现业务逻辑。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
public abstract class WorkflowActionSupport implements WorkflowActionExecutor {

    @Autowired
    protected IssueActivityMapper activityMapper;

    @Autowired
    protected SysUserMapper sysUserMapper;

    @Autowired
    protected NotificationMapper notificationMapper;

    @Autowired
    protected ProjectService projectService;

    // ===== JSON 节点读取辅助 =====

    /**
     * 从 JSON 节点中读取字符串字段。支持 fallback 到 "params" 子对象。
     */
    protected String textOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            JsonNode params = node.get("params");
            if (params != null && params.isObject()) {
                child = params.get(key);
            }
        }
        if (child == null || child.isNull()) return null;
        return child.asText();
    }

    /**
     * 从 JSON 节点中读取布尔值字段。支持字符串 "true"/"false"。
     */
    protected Boolean boolOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            JsonNode params = node.get("params");
            if (params != null && params.isObject()) {
                child = params.get(key);
            }
        }
        if (child == null || child.isNull()) return null;
        if (child.isBoolean()) return child.asBoolean();
        String text = child.asText();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        return null;
    }

    // ===== 变量插值 =====

    /**
     * 将文本中的占位符替换为实际值。
     * <p>
     * 支持：{issue.id}、{issue.summary}、{issue.key}、{issue.project.name}、
     * {{rule_name}}、{{issue_key}}、{{issue_title}}（旧格式兼容）
     */
    protected String interpolateVariables(String text, Issue issue, WorkflowRule rule) {
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
        // {issue.project.name}
        if (text.contains("{issue.project.name}")) {
            com.trackflow.project.entity.Project proj = projectService.getById(issue.getProjectId());
            String projectName = proj != null ? proj.getName() : "";
            text = text.replace("{issue.project.name}", projectName);
        }
        return text;
    }

    // ===== 活动日志 =====

    /**
     * 记录工单活动日志（source=automation）。
     */
    protected void logActivity(Long issueId, WorkflowRule rule, String action, String field, String oldVal, String newVal) {
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(rule.getCreatedBy());
        activity.setAction(action);
        activity.setFieldName(field);
        activity.setOldValue(oldVal);
        activity.setNewValue(newVal);
        // 为 ID 引用字段设置 displayValue
        if ("assignee".equals(field) || "assignee_id".equals(field)) {
            activity.setOldDisplayValue(resolveUserDisplayName(oldVal));
            activity.setNewDisplayValue(resolveUserDisplayName(newVal));
        }
        activity.setDetail("{\"source\":\"automation\",\"ruleId\":" + rule.getId()
                + ",\"ruleName\":\"" + rule.getName().replace("\"", "\\\"") + "\"}");
        activity.setSource("automation");
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    /**
     * 将用户 ID 字符串解析为显示名。
     */
    protected String resolveUserDisplayName(String idStr) {
        if (idStr == null || idStr.isBlank()) return null;
        try {
            Long userId = Long.valueOf(idStr);
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getDisplayName() : idStr;
        } catch (NumberFormatException e) {
            return idStr;
        }
    }

    // ===== 通知辅助 =====

    /**
     * 创建站内通知（用于 show_alert 和 require_field 阻断通知）。
     */
    protected void createAlertNotification(Long userId, Issue issue, WorkflowRule rule, String message, String style) {
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
        notification.setMailSent(true);
        notificationMapper.insert(notification);
    }

    // ===== 项目 ID 解析 =====

    /**
     * 解析项目 ID：支持 "same"（同项目）或数字 ID 字符串。
     */
    protected Long resolveProjectId(String projectIdStr, Issue source) {
        if (projectIdStr == null || "same".equals(projectIdStr) || projectIdStr.isBlank()) {
            return source.getProjectId();
        }
        try {
            return Long.parseLong(projectIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===== 用户解析 =====

    /**
     * 解析用户目标（reporter/assignee/creator/用户名/用户ID）为用户 ID。
     */
    protected Long resolveUserTarget(String target, Issue issue, WorkflowRule rule) {
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
     * 解析邮件目标（reporter/assignee/creator/邮箱地址/用户名）为邮箱地址。
     */
    protected String resolveEmailTarget(String target, Issue issue, WorkflowRule rule) {
        if (target.contains("@")) {
            return target;
        }
        Long userId = switch (target.toLowerCase()) {
            case "reporter" -> issue.getReporterId();
            case "assignee" -> issue.getAssigneeId();
            case "creator" -> rule.getCreatedBy();
            default -> {
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

    // ===== HTML 辅助 =====

    /**
     * 简易 HTML 转义（防止 XSS）。
     */
    protected String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ===== Issue 字段读取 =====

    /**
     * 获取 Issue 指定字段的当前值。
     */
    protected String fieldValue(Issue issue, String field) {
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
}

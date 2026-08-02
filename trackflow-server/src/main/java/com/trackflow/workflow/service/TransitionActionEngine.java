package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldValue;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.mapper.CustomFieldValueMapper;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.dto.ActionConfig;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.mapper.WorkflowActivityMapper;
import com.trackflow.workflow.strategy.AssignmentStrategy;
import com.trackflow.workflow.strategy.RoleBasedStrategy;
import com.trackflow.workflow.vo.ActionExecutionResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 转换动作引擎 —— 状态转换后执行配置的自动化动作。
 * <p>
 * 在 IssueService.transitStatus() 的同一事务中调用，不管理自己的事务。
 * 每个动作的失败不会导致状态转换回滚，通过 catch 异常 + 记录日志实现容错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitionActionEngine {

    private final ActionResolver actionResolver;
    private final ActionConfigValidator actionConfigValidator;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper issueActivityMapper;
    private final IssueCommentMapper issueCommentMapper;
    private final WorkflowActivityMapper workflowActivityMapper;
    private final NotificationService notificationService;
    private final IssueStatusMapper issueStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final List<AssignmentStrategy> strategyList;
    private final CustomFieldDefinitionMapper customFieldDefinitionMapper;
    private final CustomFieldValueMapper customFieldValueMapper;

    private Map<String, AssignmentStrategy> strategyMap;

    @PostConstruct
    public void init() {
        strategyMap = new HashMap<>();
        for (AssignmentStrategy strategy : strategyList) {
            strategyMap.put(strategy.getKey(), strategy);
        }
        log.info("[TransitionActionEngine] 已注册 {} 个分配策略: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    /**
     * 执行前置校验（状态变更前调用）。
     * <p>
     * 检查 require_field 类型的动作，验证必填字段是否已填写。
     * 如果校验失败，返回包含警告信息的 ActionExecutionResult，调用方应阻止状态转换。
     * <p>
     * 参考 YouTrack Workflow 的 issue.fields.required(field, message) 方法。
     *
     * @param issue       当前 Issue（状态未变更）
     * @param oldStatusId 当前状态 ID
     * @param newStatusId 目标状态 ID
     * @return 校验失败时返回 FIELD_VALIDATION_FAILED 结果；校验通过时返回 null
     */
    public ActionExecutionResult validatePreTransition(Issue issue, Long oldStatusId, Long newStatusId) {
        log.debug("[validatePreTransition] issue={}, oldStatus={} -> newStatus={}",
                issue.getIssueKey(), oldStatusId, newStatusId);
        
        // 解析匹配的动作列表
        List<TransitionAction> actions = actionResolver.resolve(
                issue.getProjectId(), issue.getIssueType(), oldStatusId, newStatusId);

        if (actions == null || actions.isEmpty()) {
            log.debug("[validatePreTransition] 无匹配动作，跳过校验");
            return null; // 无动作配置，校验通过
        }

        // 遍历所有 require_field 动作，检查字段是否为空
        for (TransitionAction action : actions) {
            if (!"require_field".equals(action.getActionType())) {
                continue;
            }

            log.debug("[validatePreTransition] 检查 require_field 动作: actionId={}", action.getId());
            ActionExecutionResult validationResult = validateRequiredField(action, issue);
            if (validationResult != null) {
                // 校验失败，立即返回（第一个失败的字段）
                return validationResult;
            }
        }

        return null; // 所有校验通过
    }

    /**
     * 校验单个必填字段。
     *
     * @return 校验失败时返回 FIELD_VALIDATION_FAILED 结果；校验通过时返回 null
     */
    private ActionExecutionResult validateRequiredField(TransitionAction action, Issue issue) {
        ActionConfig config = actionConfigValidator.parseConfig(action.getActionConfig());
        if (config == null) {
            log.warn("[validateRequiredField] action {} 的 action_config 解析失败", action.getId());
            return null;
        }
        
        Long fieldId = config.getRequiredFieldId();
        if (fieldId == null) {
            log.warn("[validateRequiredField] action {} 缺少 required_field_id 配置", action.getId());
            return null; // 配置不完整，跳过此动作
        }

        // 获取字段定义
        CustomFieldDefinition fieldDef = customFieldDefinitionMapper.selectById(fieldId);
        if (fieldDef == null) {
            log.warn("[validateRequiredField] action {} 引用的字段 {} 不存在", action.getId(), fieldId);
            return null; // 字段不存在，跳过此动作
        }

        // 检查字段值是否为空
        CustomFieldValue fieldValue = customFieldValueMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issue.getId())
                        .eq(CustomFieldValue::getCustomFieldId, fieldId)
                        .last("LIMIT 1")
        );

        boolean isEmpty = (fieldValue == null || fieldValue.getValue() == null 
                || fieldValue.getValue().isBlank());

        if (isEmpty) {
            // 字段为空，构造警告消息
            String fieldName = config.getRequiredFieldName() != null 
                    ? config.getRequiredFieldName() 
                    : fieldDef.getName();
            String warningMessage = config.getWarningMessage();
            if (warningMessage == null || warningMessage.isBlank()) {
                warningMessage = "请先填写「{field_name}」字段";
            }
            warningMessage = warningMessage.replace("{field_name}", fieldName);

            log.info("[TransitionActionEngine] Issue {} 前置校验失败：字段「{}」为空",
                    issue.getIssueKey(), fieldName);

            return ActionExecutionResult.fieldValidationFailed(fieldId, fieldName, warningMessage);
        }

        return null; // 校验通过
    }

    /**
     * 执行转换动作。
     *
     * @param issue                当前 Issue（状态已更新）
     * @param oldStatusId          转换前状态 ID
     * @param newStatusId          转换后状态 ID
     * @param triggeredBy          触发转换的用户 ID
     * @param explicitAssigneeId   用户显式指定的 assignee（null 表示 unassign）
     * @param assigneeExplicitlySet 是否显式设置了 assignee（区分"未传"和"传了null"）
     * @return 动作执行结果摘要
     */
    public ActionExecutionResult execute(Issue issue, Long oldStatusId, Long newStatusId,
                        Long triggeredBy, Long explicitAssigneeId,
                        boolean assigneeExplicitlySet) {

        // 1. Manual Override: 用户显式指定了 assignee
        if (assigneeExplicitlySet) {
            issue.setAssigneeId(explicitAssigneeId);
            issueMapper.updateById(issue);

            // 记录 auto_assign_skipped activity
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issue.getId());
            activity.setUserId(triggeredBy);
            activity.setAction("auto_assign_skipped");
            activity.setDetail("{\"reason\":\"manual_override\"}");
            activity.setCreatedAt(LocalDateTime.now());
            issueActivityMapper.insert(activity);

            log.info("[TransitionActionEngine] Issue {} manual override, assignee set to {}",
                    issue.getId(), explicitAssigneeId);
            return ActionExecutionResult.manualOverride(
                    explicitAssigneeId, getUserDisplayName(explicitAssigneeId));
        }

        // 2. 解析匹配的动作列表
        List<TransitionAction> actions = actionResolver.resolve(
                issue.getProjectId(), issue.getIssueType(), oldStatusId, newStatusId);

        if (actions == null || actions.isEmpty()) {
            return ActionExecutionResult.noActions();
        }

        // 3. 按 sort_order 顺序执行每个动作（所有动作都执行，不因第一个成功而早退）
        ActionExecutionResult lastAssignResult = null;
        boolean hasError = false;
        for (TransitionAction action : actions) {
            try {
                ActionExecutionResult result = executeActionWithResult(
                        action, issue, triggeredBy, oldStatusId, newStatusId);
                if (result != null && result.isExecuted()) {
                    if ("auto_assign".equals(action.getActionType())) {
                        // 记录最后一个成功的 auto_assign 结果（通常只配一个，但仍继续执行后续动作）
                        lastAssignResult = result;
                    }
                    // 不 return，继续执行后续动作
                }
            } catch (RuntimeException e) {
                // 4. 错误处理：记录错误，继续执行下一个动作（单个动作失败不影响其他动作）
                log.error("[TransitionActionEngine] Issue {} action {} 执行异常: {}",
                        issue.getId(), action.getId(), e.getMessage(), e);
                recordWorkflowFailure(action, issue, e);
                hasError = true;
            }
        }

        // 所有动作执行完成后，返回最终结果
        if (lastAssignResult != null) {
            return lastAssignResult;
        }
        return hasError ? ActionExecutionResult.executionError()
                : ActionExecutionResult.strategyFailed();
    }

    /**
     * 创建工单时执行自动分配。
     * <p>
     * 仅在用户未显式指定 assignee 时调用。
     * 匹配 old_status_id IS NULL 的规则。
     *
     * @param issue       刚创建的 Issue（已 insert 入库）
     * @param creatorId   创建者用户 ID
     */
    public void executeOnCreate(Issue issue, Long creatorId) {
        // 解析创建时的动作列表
        List<TransitionAction> actions = actionResolver.resolveOnCreate(
                issue.getProjectId(), issue.getIssueType(), issue.getStatusId());

        if (actions == null || actions.isEmpty()) {
            log.debug("[TransitionActionEngine] Issue {} no on-create actions found", issue.getId());
            return;
        }

        // 按 sort_order 顺序执行（所有动作都执行，auto_assign 找到第一个成功后不再尝试后续的 auto_assign）
        boolean autoAssigned = false;
        for (TransitionAction action : actions) {
            try {
                if ("auto_assign".equals(action.getActionType()) && autoAssigned) {
                    // 已有 auto_assign 成功，跳过同类型后续动作（创建时只分配一次）
                    log.debug("[TransitionActionEngine] Issue {} on-create: skip duplicate auto_assign action_id={}",
                            issue.getId(), action.getId());
                    continue;
                }
                boolean executed = executeCreateAction(action, issue, creatorId);
                if (executed && "auto_assign".equals(action.getActionType())) {
                    autoAssigned = true;
                }
            } catch (RuntimeException e) {
                log.error("[TransitionActionEngine] Issue {} on-create action {} 执行异常: {}",
                        issue.getId(), action.getId(), e.getMessage(), e);
                recordWorkflowFailure(action, issue, e);
            }
        }
    }

    /**
     * 执行单个动作并返回结果。
     *
     * @return ActionExecutionResult（成功时 executed=true），null 表示动作未执行（策略失败或跳过）
     */
    private ActionExecutionResult executeActionWithResult(TransitionAction action, Issue issue, Long triggeredBy,
                                  Long oldStatusId, Long newStatusId) {
        String actionType = action.getActionType();

        switch (actionType) {
            case "auto_assign":
                return executeAutoAssignAction(action, issue, triggeredBy, oldStatusId, newStatusId);
            case "add_comment":
                return executeAddCommentAction(action, issue, triggeredBy, oldStatusId, newStatusId);
            default:
                log.debug("[TransitionActionEngine] 跳过未支持的动作类型: type={}, action_id={}",
                        actionType, action.getId());
                return null;
        }
    }

    /**
     * 执行 auto_assign 动作：自动分配负责人。
     */
    private ActionExecutionResult executeAutoAssignAction(TransitionAction action, Issue issue, Long triggeredBy,
                                                          Long oldStatusId, Long newStatusId) {
        // 解析 action_config
        ActionConfig config = actionConfigValidator.parseConfig(action.getActionConfig());
        if (config == null) {
            log.error("[TransitionActionEngine] Issue {} action {} action_config 解析失败, json={}",
                    issue.getId(), action.getId(), action.getActionConfig());
            return null;
        }

        // 查找主策略
        String strategyKey = config.getStrategy();
        AssignmentStrategy strategy = strategyMap.get(strategyKey);
        if (strategy == null) {
            log.warn("[TransitionActionEngine] 未找到策略: {}, action_id={}",
                    strategyKey, action.getId());
            return null;
        }

        // 执行主策略
        Long result = strategy.resolve(issue, config, issue.getProjectId());

        // 处理 KEEP_EXISTING_ASSIGNEE 特殊返回值（当前负责人已属于目标角色，保留不变）
        if (RoleBasedStrategy.KEEP_EXISTING_ASSIGNEE.equals(result)) {
            Long currentAssigneeId = issue.getAssigneeId();
            String currentAssigneeName = getUserDisplayName(currentAssigneeId);

            // 记录 auto_assign_skipped activity
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issue.getId());
            activity.setUserId(triggeredBy);
            activity.setAction("auto_assign_skipped");
            activity.setDetail(String.format(
                    "{\"reason\":\"existing_assignee_matches_role\",\"assignee_id\":%d,\"role_id\":%s,\"action_id\":%d}",
                    currentAssigneeId, config.getRoleId(), action.getId()));
            activity.setCreatedAt(LocalDateTime.now());
            issueActivityMapper.insert(activity);

            log.info("[TransitionActionEngine] Issue {} 保留现有负责人 {} (已属于目标角色 {})",
                    issue.getId(), currentAssigneeId, config.getRoleId());

            return ActionExecutionResult.keptExisting(currentAssigneeId, currentAssigneeName, strategyKey);
        }

        // 主策略失败时尝试 fallback
        String usedStrategy = strategyKey;
        if (result == null && config.getFallbackStrategy() != null
                && !config.getFallbackStrategy().isBlank()) {
            String fallbackKey = config.getFallbackStrategy();
            AssignmentStrategy fallbackStrategy = strategyMap.get(fallbackKey);
            if (fallbackStrategy != null) {
                log.debug("[TransitionActionEngine] 主策略 {} 返回 null，尝试 fallback: {}",
                        strategyKey, fallbackKey);
                result = fallbackStrategy.resolve(issue, config, issue.getProjectId());
                if (result != null) {
                    usedStrategy = fallbackKey;
                }
            } else {
                log.warn("[TransitionActionEngine] fallback 策略未找到: {}", fallbackKey);
            }
        }

        // 解析成功：更新 assignee 并记录活动
        if (result != null) {
            Long oldAssigneeId = issue.getAssigneeId();
            issue.setAssigneeId(result);
            issueMapper.updateById(issue);

            // 记录 auto_assigned activity（存储 ID + 显示名，前端通过 COALESCE 优先展示 displayValue）
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issue.getId());
            activity.setUserId(triggeredBy);
            activity.setAction("auto_assigned");
            activity.setFieldName("assignee_id");
            activity.setOldValue(oldAssigneeId != null ? String.valueOf(oldAssigneeId) : null);
            activity.setNewValue(String.valueOf(result));
            activity.setOldDisplayValue(oldAssigneeId != null ? getUserDisplayName(oldAssigneeId) : null);
            activity.setNewDisplayValue(getUserDisplayName(result));
            activity.setDetail(String.format(
                    "{\"action_id\":%d,\"strategy\":\"%s\",\"triggered_by\":%d}",
                    action.getId(), usedStrategy, triggeredBy));
            activity.setCreatedAt(LocalDateTime.now());
            issueActivityMapper.insert(activity);

            log.info("[TransitionActionEngine] Issue {} auto-assigned to user {} (strategy: {})",
                    issue.getId(), result, usedStrategy);

            // 发送通知给新 assignee（跳过自我通知，失败不影响动作执行）
            sendAutoAssignNotification(issue, result, triggeredBy, oldStatusId, newStatusId);

            return ActionExecutionResult.assigned(result, getUserDisplayName(result), usedStrategy);
        }

        // 主策略 + fallback 都失败
        log.warn("[TransitionActionEngine] Failed to resolve assignee for issue {} (action_id={}, strategy={})",
                issue.getId(), action.getId(), strategyKey);
        return null;
    }

    /**
     * 执行 add_comment 动作：自动添加系统评论。
     */
    private ActionExecutionResult executeAddCommentAction(TransitionAction action, Issue issue, Long triggeredBy,
                                                          Long oldStatusId, Long newStatusId) {
        // 解析 action_config 获取评论模板
        ActionConfig config = actionConfigValidator.parseConfig(action.getActionConfig());
        String commentText;
        if (config != null && config.getCommentTemplate() != null && !config.getCommentTemplate().isBlank()) {
            // 支持简单占位符替换
            commentText = config.getCommentTemplate()
                    .replace("{issue_key}", issue.getIssueKey() != null ? issue.getIssueKey() : "")
                    .replace("{old_status}", getStatusName(oldStatusId))
                    .replace("{new_status}", getStatusName(newStatusId));
        } else {
            // 默认模板
            commentText = String.format("状态已从「%s」变更为「%s」。",
                    getStatusName(oldStatusId), getStatusName(newStatusId));
        }

        // 插入系统评论（source = "workflow_action" 区分手动评论）
        IssueComment comment = new IssueComment();
        comment.setIssueId(issue.getId());
        comment.setUserId(triggeredBy);
        comment.setContent(commentText);
        comment.setSource("workflow_action");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        issueCommentMapper.insert(comment);

        log.info("[TransitionActionEngine] Issue {} auto add_comment via action_id={}",
                issue.getId(), action.getId());

        return ActionExecutionResult.commentAdded();
    }

    /**
     * 执行创建时的单个动作。
     * <p>
     * 与 executeActionWithResult 区别：
     * - 不传 oldStatusId（创建时无先前状态）
     * - 通知内容为"创建时自动分配"
     *
     * @return true 如果成功执行了动作
     */
    private boolean executeCreateAction(TransitionAction action, Issue issue, Long creatorId) {
        String actionType = action.getActionType();
        switch (actionType) {
            case "auto_assign":
                return executeCreateAutoAssignAction(action, issue, creatorId);
            case "add_comment":
                return executeCreateAddCommentAction(action, issue, creatorId);
            default:
                log.debug("[TransitionActionEngine] on-create 跳过未支持的动作类型: type={}, action_id={}",
                        actionType, action.getId());
                return false;
        }
    }

    /**
     * 创建时执行 auto_assign 动作。
     */
    private boolean executeCreateAutoAssignAction(TransitionAction action, Issue issue, Long creatorId) {
        ActionConfig config = actionConfigValidator.parseConfig(action.getActionConfig());
        if (config == null) {
            log.error("[TransitionActionEngine] Issue {} on-create action {} config 解析失败, json={}",
                    issue.getId(), action.getId(), action.getActionConfig());
            return false;
        }

        String strategyKey = config.getStrategy();
        AssignmentStrategy strategy = strategyMap.get(strategyKey);
        if (strategy == null) {
            log.warn("[TransitionActionEngine] 未找到策略: {}, action_id={}", strategyKey, action.getId());
            return false;
        }

        Long result = strategy.resolve(issue, config, issue.getProjectId());

        // 处理 KEEP_EXISTING_ASSIGNEE 特殊返回值（创建时通常不会触发，但保持一致性）
        if (RoleBasedStrategy.KEEP_EXISTING_ASSIGNEE.equals(result)) {
            log.info("[TransitionActionEngine] Issue {} on-create: 保留现有负责人 {} (已属于目标角色 {})",
                    issue.getId(), issue.getAssigneeId(), config.getRoleId());
            return true; // 视为成功执行，只是决定保留
        }

        // fallback
        if (result == null && config.getFallbackStrategy() != null
                && !config.getFallbackStrategy().isBlank()) {
            AssignmentStrategy fallbackStrategy = strategyMap.get(config.getFallbackStrategy());
            if (fallbackStrategy != null) {
                result = fallbackStrategy.resolve(issue, config, issue.getProjectId());
                // fallback 也可能返回 KEEP_EXISTING
                if (RoleBasedStrategy.KEEP_EXISTING_ASSIGNEE.equals(result)) {
                    log.info("[TransitionActionEngine] Issue {} on-create fallback: 保留现有负责人",
                            issue.getId());
                    return true;
                }
            }
        }

        if (result != null) {
            issue.setAssigneeId(result);
            issueMapper.updateById(issue);

            // 记录 auto_assigned activity（创建时无 oldValue，只设置 newDisplayValue）
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issue.getId());
            activity.setUserId(creatorId);
            activity.setAction("auto_assigned");
            activity.setFieldName("assignee_id");
            activity.setOldValue(null);
            activity.setNewValue(String.valueOf(result));
            activity.setNewDisplayValue(getUserDisplayName(result));
            activity.setDetail(String.format(
                    "{\"action_id\":%d,\"strategy\":\"%s\",\"trigger\":\"on_create\",\"triggered_by\":%d}",
                    action.getId(), strategyKey, creatorId));
            activity.setCreatedAt(LocalDateTime.now());
            issueActivityMapper.insert(activity);

            log.info("[TransitionActionEngine] Issue {} auto-assigned on create to user {} (strategy: {})",
                    issue.getId(), result, strategyKey);

            // 通知由 IssueNotificationEvent.Created 事件统一处理（issue 对象已更新 assigneeId）

            return true;
        }

        log.warn("[TransitionActionEngine] On-create auto-assign failed for issue {} (action_id={}, strategy={})",
                issue.getId(), action.getId(), strategyKey);
        return false;
    }

    /**
     * 创建时执行 add_comment 动作（添加系统评论）。
     */
    private boolean executeCreateAddCommentAction(TransitionAction action, Issue issue, Long creatorId) {
        ActionConfig config = actionConfigValidator.parseConfig(action.getActionConfig());
        String commentText;
        if (config != null && config.getCommentTemplate() != null && !config.getCommentTemplate().isBlank()) {
            commentText = config.getCommentTemplate()
                    .replace("{issue_key}", issue.getIssueKey() != null ? issue.getIssueKey() : "")
                    .replace("{old_status}", "")
                    .replace("{new_status}", getStatusName(issue.getStatusId()));
        } else {
            commentText = "工单已创建。";
        }

        IssueComment comment = new IssueComment();
        comment.setIssueId(issue.getId());
        comment.setUserId(creatorId);
        comment.setContent(commentText);
        comment.setSource("workflow_action");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        issueCommentMapper.insert(comment);

        log.info("[TransitionActionEngine] Issue {} on-create add_comment via action_id={}",
                issue.getId(), action.getId());
        return true;
    }

    /**
     * 发送自动分配通知给新 assignee。
     * 跳过自我通知（new assignee == triggeredBy），失败不影响动作执行。
     */
    private void sendAutoAssignNotification(Issue issue, Long newAssigneeId,
                                            Long triggeredBy, Long oldStatusId, Long newStatusId) {
        try {
            // 跳过自我通知
            if (newAssigneeId.equals(triggeredBy)) {
                log.debug("[TransitionActionEngine] 跳过自我通知: userId={}", newAssigneeId);
                return;
            }

            // 获取状态名称
            String oldStatusName = getStatusName(oldStatusId);
            String newStatusName = getStatusName(newStatusId);

            // 获取触发者信息
            String triggeredByName = getUserDisplayName(triggeredBy);

            // 构建通知内容
            String title = String.format("工单 %s 已自动分配给你", issue.getIssueKey());
            String content = String.format(
                    "工单 [%s] %s 状态从「%s」变更为「%s」，已自动分配给你。触发者：%s",
                    issue.getIssueKey(), issue.getTitle(),
                    oldStatusName, newStatusName, triggeredByName);

            notificationService.notify(
                    newAssigneeId,
                    triggeredBy,
                    title,
                    content,
                    NotificationType.issue_auto_assigned,
                    NotificationReason.auto_assigned,
                    "issue",
                    issue.getId(),
                    issue.getProjectId()
            );

            log.debug("[TransitionActionEngine] 已发送自动分配通知: issueId={}, assigneeId={}",
                    issue.getId(), newAssigneeId);
        } catch (Exception e) {
            log.error("[TransitionActionEngine] 发送自动分配通知失败: issueId={}, assigneeId={}, error={}",
                    issue.getId(), newAssigneeId, e.getMessage(), e);
        }
    }

    /**
     * 获取状态的中文显示名称，查找失败时返回 ID 字符串。
     */
    private String getStatusName(Long statusId) {
        if (statusId == null) {
            return "未知";
        }
        try {
            IssueStatus status = issueStatusMapper.selectById(statusId);
            return status != null ? status.getLocalizedName() : String.valueOf(statusId);
        } catch (Exception e) {
            return String.valueOf(statusId);
        }
    }

    /**
     * 获取用户显示名称，查找失败时返回 ID 字符串。
     */
    private String getUserDisplayName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getDisplayName() : String.valueOf(userId);
        } catch (Exception e) {
            return String.valueOf(userId);
        }
    }

    /**
     * 记录动作执行失败到 workflow_activity 表。
     */
    private void recordWorkflowFailure(TransitionAction action, Issue issue, RuntimeException e) {
        try {
            WorkflowActivity wa = new WorkflowActivity();
            wa.setProjectId(issue.getProjectId());
            wa.setIssueType(issue.getIssueType());
            wa.setUserId(null);
            wa.setAction("action_execution_failed");
            wa.setOldValue(String.format("action_id=%d, issue_id=%d, action_type=%s",
                    action.getId(), issue.getId(), action.getActionType()));
            wa.setNewValue(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            wa.setCreatedAt(LocalDateTime.now());
            workflowActivityMapper.insert(wa);
        } catch (Exception ex) {
            log.error("[TransitionActionEngine] 记录 workflow_activity 失败: {}", ex.getMessage(), ex);
        }
    }
}

package com.trackflow.workflow.service;

import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.dto.ActionConfig;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.mapper.WorkflowActivityMapper;
import com.trackflow.workflow.strategy.AssignmentStrategy;
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
    private final WorkflowActivityMapper workflowActivityMapper;
    private final NotificationService notificationService;
    private final IssueStatusMapper issueStatusMapper;
    private final SysUserMapper sysUserMapper;
    private final List<AssignmentStrategy> strategyList;

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
     * 执行转换动作。
     *
     * @param issue                当前 Issue（状态已更新）
     * @param oldStatusId          转换前状态 ID
     * @param newStatusId          转换后状态 ID
     * @param triggeredBy          触发转换的用户 ID
     * @param explicitAssigneeId   用户显式指定的 assignee（null 表示 unassign）
     * @param assigneeExplicitlySet 是否显式设置了 assignee（区分"未传"和"传了null"）
     */
    public void execute(Issue issue, Long oldStatusId, Long newStatusId,
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
            return;
        }

        // 2. 解析匹配的动作列表
        List<TransitionAction> actions = actionResolver.resolve(
                issue.getProjectId(), issue.getIssueType(), oldStatusId, newStatusId);

        if (actions == null || actions.isEmpty()) {
            return;
        }

        // 3. 按 sort_order 顺序执行每个动作
        for (TransitionAction action : actions) {
            try {
                boolean assigned = executeAction(action, issue, triggeredBy, oldStatusId, newStatusId);
                if (assigned) {
                    // 第一个成功的 auto_assign 后停止（不重复分配）
                    break;
                }
            } catch (RuntimeException e) {
                // 4. 错误处理：记录错误，继续执行下一个动作
                log.error("[TransitionActionEngine] Issue {} action {} 执行异常: {}",
                        issue.getId(), action.getId(), e.getMessage(), e);
                recordWorkflowFailure(action, issue, e);
            }
        }
    }

    /**
     * 执行单个动作。
     *
     * @return true 如果成功执行了 auto_assign（assignee 已更新），false 表示跳过或失败
     */
    private boolean executeAction(TransitionAction action, Issue issue, Long triggeredBy,
                                  Long oldStatusId, Long newStatusId) {
        // 仅处理 auto_assign 类型（未来可扩展 switch）
        if (!"auto_assign".equals(action.getActionType())) {
            log.debug("[TransitionActionEngine] 跳过非 auto_assign 动作: type={}",
                    action.getActionType());
            return false;
        }

        // 解析 action_config
        ActionConfig config = actionConfigValidator.parseConfig(action.getActionConfig());
        if (config == null) {
            log.error("[TransitionActionEngine] Issue {} action {} action_config 解析失败, json={}",
                    issue.getId(), action.getId(), action.getActionConfig());
            return false;
        }

        // 查找主策略
        String strategyKey = config.getStrategy();
        AssignmentStrategy strategy = strategyMap.get(strategyKey);
        if (strategy == null) {
            log.warn("[TransitionActionEngine] 未找到策略: {}, action_id={}",
                    strategyKey, action.getId());
            return false;
        }

        // 执行主策略
        Long result = strategy.resolve(issue, config, issue.getProjectId());

        // 主策略失败时尝试 fallback
        if (result == null && config.getFallbackStrategy() != null
                && !config.getFallbackStrategy().isBlank()) {
            String fallbackKey = config.getFallbackStrategy();
            AssignmentStrategy fallbackStrategy = strategyMap.get(fallbackKey);
            if (fallbackStrategy != null) {
                log.debug("[TransitionActionEngine] 主策略 {} 返回 null，尝试 fallback: {}",
                        strategyKey, fallbackKey);
                result = fallbackStrategy.resolve(issue, config, issue.getProjectId());
            } else {
                log.warn("[TransitionActionEngine] fallback 策略未找到: {}", fallbackKey);
            }
        }

        // 解析成功：更新 assignee 并记录活动
        if (result != null) {
            Long oldAssigneeId = issue.getAssigneeId();
            issue.setAssigneeId(result);
            issueMapper.updateById(issue);

            // 记录 auto_assigned activity
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issue.getId());
            activity.setUserId(triggeredBy);
            activity.setAction("auto_assigned");
            activity.setFieldName("assignee_id");
            activity.setOldValue(oldAssigneeId != null ? String.valueOf(oldAssigneeId) : null);
            activity.setNewValue(String.valueOf(result));
            activity.setDetail(String.format(
                    "{\"action_id\":%d,\"strategy\":\"%s\",\"triggered_by\":%d}",
                    action.getId(), strategyKey, triggeredBy));
            activity.setCreatedAt(LocalDateTime.now());
            issueActivityMapper.insert(activity);

            log.info("[TransitionActionEngine] Issue {} auto-assigned to user {} (strategy: {})",
                    issue.getId(), result, strategyKey);

            // 发送通知给新 assignee（跳过自我通知，失败不影响动作执行）
            sendAutoAssignNotification(issue, result, triggeredBy, oldStatusId, newStatusId);

            return true;
        }

        // 主策略 + fallback 都失败
        log.warn("[TransitionActionEngine] Failed to resolve assignee for issue {} (action_id={}, strategy={})",
                issue.getId(), action.getId(), strategyKey);
        return false;
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
                    "issue",
                    issue.getId()
            );

            log.debug("[TransitionActionEngine] 已发送自动分配通知: issueId={}, assigneeId={}",
                    issue.getId(), newAssigneeId);
        } catch (Exception e) {
            log.error("[TransitionActionEngine] 发送自动分配通知失败: issueId={}, assigneeId={}, error={}",
                    issue.getId(), newAssigneeId, e.getMessage(), e);
        }
    }

    /**
     * 获取状态名称，查找失败时返回 ID 字符串。
     */
    private String getStatusName(Long statusId) {
        if (statusId == null) {
            return "未知";
        }
        try {
            IssueStatus status = issueStatusMapper.selectById(statusId);
            return status != null ? status.getName() : String.valueOf(statusId);
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

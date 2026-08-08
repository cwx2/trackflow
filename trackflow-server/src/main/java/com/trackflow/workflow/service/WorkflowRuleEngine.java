package com.trackflow.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.entity.WorkflowRuleExecutionLog;
import com.trackflow.workflow.mapper.WorkflowRuleExecutionLogMapper;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import com.trackflow.workflow.service.action.ActionExecutionContext;
import com.trackflow.workflow.service.action.ActionResult;
import com.trackflow.workflow.service.action.WorkflowActionExecutor;
import com.trackflow.workflow.service.action.WorkflowActionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流规则引擎 — 触发、评估条件、通过策略模式分发动作执行。
 * <p>
 * 动作执行通过 {@link WorkflowActionRegistry} 自动发现所有 {@link WorkflowActionExecutor} 实现，
 * 新增动作类型只需创建新的 @Component，无需修改此类。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuleEngine {

    private final WorkflowRuleMapper ruleMapper;
    private final IssueMapper issueMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final WorkflowRuleExecutionLogMapper executionLogMapper;
    private final com.trackflow.common.rule.RuleConditionEvaluator ruleConditionEvaluator;
    private final WorkflowActionRegistry actionRegistry;

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
            log.warn("[RuleEngine] Redis 不可用，链式深度计数降级跳过，issueId={}, field={}", issueId, changedField);
            depth = 1L;
        }
        if (depth == 1L) {
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
     * 仅供 {@link ScheduledRuleService} 等内部调度场景使用。
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

    /**
     * 供 ScheduledRuleService 调用——对单个工单执行规则的所有动作。
     */
    public void executeActionsForSchedule(WorkflowRule rule, Issue issue) {
        executeActions(rule, issue);
    }

    // ===== 核心动作执行（策略模式分发） =====

    private void executeActions(WorkflowRule rule, Issue issue) {
        String json = rule.getActionJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return;
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) return;

            boolean modified = false;
            ActionExecutionContext context = new ActionExecutionContext();

            for (JsonNode act : arr) {
                String type = act.has("type") ? act.get("type").asText() : null;
                if (type == null || type.isBlank()) continue;

                var executor = actionRegistry.getExecutor(type);
                if (executor.isPresent()) {
                    ActionResult result = executor.get().execute(act, issue, rule, context);
                    if (result.issueModified()) modified = true;
                    if (result.haltExecution()) {
                        log.info("[RuleEngine] Action '{}' halted execution in rule '{}' (id={})",
                                type, rule.getName(), rule.getId());
                        break;
                    }
                } else {
                    log.warn("[RuleEngine] Unknown action type '{}' in rule '{}' (id={}). Registered types: {}",
                            type, rule.getName(), rule.getId(), actionRegistry.getRegisteredTypes());
                }
            }

            if (modified) issueMapper.updateById(issue);
        } catch (Exception e) {
            log.warn("[RuleEngine] Action exec error for rule '{}': {}", rule.getName(), e.getMessage());
        }
    }

    // ===== 条件评估 =====

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
            boolean success = false;
            String errorMessage = null;
            try {
                if (evaluateConditions(rule, issue, changedField, oldValue)) {
                    executeActions(rule, issue);
                    success = true;
                }
            } catch (Exception e) {
                log.warn("[RuleEngine] Rule '{}' (id={}) failed for issue {}: {}",
                        rule.getName(), rule.getId(), issue.getId(), e.getMessage());
                errorMessage = e.getMessage();
            } finally {
                int duration = (int) (System.currentTimeMillis() - startTime);
                recordExecutionLog(rule, issue, duration, success, errorMessage);
            }
        }
    }

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
}

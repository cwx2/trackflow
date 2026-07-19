package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import com.trackflow.workflow.entity.WorkflowRuleExecutionLog;
import com.trackflow.workflow.mapper.WorkflowRuleExecutionLogMapper;
import com.trackflow.workflow.mapper.WorkflowRuleMapper;
import com.trackflow.workflow.vo.WorkflowRuleExecutionLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * On-schedule 规则调度执行服务。
 * <p>
 * 每分钟检查一次所有已启用的 on_schedule 规则，判断是否到了执行时间。
 * 到时间则查询匹配工单，批量执行动作，并记录执行日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledRuleService {

    private final WorkflowRuleMapper ruleMapper;
    private final WorkflowRuleExecutionLogMapper logMapper;
    private final IssueMapper issueMapper;
    private final WorkflowRuleEngine ruleEngine;
    private final ObjectMapper objectMapper;

    /**
     * 每分钟执行一次调度检查。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void checkAndExecuteScheduledRules() {
        List<WorkflowRule> rules = ruleMapper.findEnabledScheduledRules();
        if (rules.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (WorkflowRule rule : rules) {
            try {
                if (shouldExecuteNow(rule, now)) {
                    executeRule(rule, now);
                }
            } catch (Exception e) {
                log.error("[ScheduledRule] 规则 '{}' (id={}) 调度检查异常: {}",
                        rule.getName(), rule.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 手动触发执行规则（用于测试）。
     */
    @Transactional
    public WorkflowRuleExecutionLogVO executeRuleManually(Long ruleId) {
        WorkflowRule rule = ruleMapper.selectById(ruleId);
        if (rule == null || !"on_schedule".equals(rule.getRuleType())) {
            throw new com.trackflow.common.exception.BusinessException(
                    com.trackflow.common.exception.ErrorCode.RESOURCE_NOT_FOUND,
                    "定时规则不存在");
        }
        return executeRule(rule, LocalDateTime.now());
    }

    /**
     * 查询规则的执行日志（最近 N 条）。
     */
    public List<WorkflowRuleExecutionLogVO> getExecutionLogs(Long ruleId, int limit) {
        List<WorkflowRuleExecutionLog> logs = logMapper.findRecentLogs(ruleId, limit);
        return logs.stream().map(this::toLogVO).toList();
    }

    // ============ 核心执行逻辑 ============

    @Transactional
    protected WorkflowRuleExecutionLogVO executeRule(WorkflowRule rule, LocalDateTime now) {
        long startTime = System.currentTimeMillis();
        int matched = 0, success = 0, failure = 0;
        String errorMessage = null;

        try {
            // 1. 查询匹配工单
            List<Issue> matchedIssues = findMatchingIssues(rule);
            matched = matchedIssues.size();

            // 2. 对每个匹配工单执行动作
            for (Issue issue : matchedIssues) {
                try {
                    ruleEngine.executeActionsForSchedule(rule, issue);
                    success++;
                } catch (Exception e) {
                    failure++;
                    log.warn("[ScheduledRule] 规则 '{}' 对工单 {} 执行失败: {}",
                            rule.getName(), issue.getIssueKey(), e.getMessage());
                    if (errorMessage == null) {
                        errorMessage = "工单 " + issue.getIssueKey() + ": " + e.getMessage();
                    }
                }
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("[ScheduledRule] 规则 '{}' 执行异常: {}", rule.getName(), e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;

        // 3. 更新规则的 last_executed_at
        rule.setLastExecutedAt(now);
        ruleMapper.updateById(rule);

        // 4. 记录执行日志
        WorkflowRuleExecutionLog logEntry = new WorkflowRuleExecutionLog();
        logEntry.setRuleId(rule.getId());
        logEntry.setExecutedAt(now);
        logEntry.setMatchedCount(matched);
        logEntry.setSuccessCount(success);
        logEntry.setFailureCount(failure);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setDurationMs((int) duration);
        logMapper.insert(logEntry);

        log.info("[ScheduledRule] 规则 '{}' 执行完成: 匹配={}, 成功={}, 失败={}, 耗时={}ms",
                rule.getName(), matched, success, failure, duration);

        return toLogVO(logEntry);
    }

    // ============ 工单匹配 ============

    private List<Issue> findMatchingIssues(WorkflowRule rule) {
        // 构建基础查询条件
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Issue::getDeletedAt);

        // 限定项目
        if (rule.getProjectId() != null) {
            wrapper.eq(Issue::getProjectId, rule.getProjectId());
        }

        // 先查出候选工单，再用规则条件过滤
        List<Issue> candidates = issueMapper.selectList(wrapper);

        // 使用 conditionJson 过滤
        String condJson = rule.getConditionJson();
        if (condJson == null || condJson.isBlank() || "[]".equals(condJson.trim())) {
            return candidates;
        }

        try {
            JsonNode conditions = objectMapper.readTree(condJson);
            if (!conditions.isArray() || conditions.isEmpty()) {
                return candidates;
            }
            return candidates.stream()
                    .filter(issue -> evaluateScheduleConditions(conditions, issue))
                    .toList();
        } catch (Exception e) {
            log.warn("[ScheduledRule] 条件解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean evaluateScheduleConditions(JsonNode conditions, Issue issue) {
        for (JsonNode cond : conditions) {
            if (!evalSingleCondition(cond, issue)) {
                return false;
            }
        }
        return true;
    }

    private boolean evalSingleCondition(JsonNode cond, Issue issue) {
        String field = textOf(cond, "field");
        String operator = textOf(cond, "operator");
        String expected = textOf(cond, "value");
        if (field == null || operator == null) return true;

        String actual = getFieldValue(issue, field);

        return switch (operator) {
            case "equals" -> Objects.equals(actual, expected);
            case "not_equals" -> !Objects.equals(actual, expected);
            case "contains" -> actual != null && expected != null
                    && actual.toLowerCase().contains(expected.toLowerCase());
            case "in" -> actual != null && expected != null
                    && new HashSet<>(Arrays.asList(expected.split(","))).contains(actual);
            case "is_empty" -> actual == null || actual.isBlank();
            case "is_not_empty" -> actual != null && !actual.isBlank();
            case "overdue" -> isOverdue(issue);
            case "due_within_days" -> isDueWithinDays(issue, expected);
            default -> true;
        };
    }

    private String getFieldValue(Issue issue, String field) {
        return switch (field) {
            case "type", "issue_type" -> issue.getIssueType();
            case "priority" -> issue.getPriority();
            case "status", "status_id" ->
                    issue.getStatusId() != null ? String.valueOf(issue.getStatusId()) : null;
            case "assignee", "assignee_id" ->
                    issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null;
            case "reporter", "reporter_id" ->
                    issue.getReporterId() != null ? String.valueOf(issue.getReporterId()) : null;
            case "sprint", "sprint_id" ->
                    issue.getSprintId() != null ? String.valueOf(issue.getSprintId()) : null;
            case "title" -> issue.getTitle();
            default -> null;
        };
    }

    private boolean isOverdue(Issue issue) {
        if (issue.getDueDate() == null) return false;
        return issue.getDueDate().isBefore(LocalDate.now());
    }

    private boolean isDueWithinDays(Issue issue, String daysStr) {
        if (issue.getDueDate() == null || daysStr == null) return false;
        try {
            int days = Integer.parseInt(daysStr);
            LocalDate today = LocalDate.now();
            LocalDate due = issue.getDueDate();
            return !due.isBefore(today) && due.isBefore(today.plusDays(days + 1));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ============ 调度时间判断 ============

    private boolean shouldExecuteNow(WorkflowRule rule, LocalDateTime now) {
        String cron = rule.getCronExpression();
        if (cron == null || cron.isBlank()) return false;

        LocalDateTime lastExec = rule.getLastExecutedAt();

        return switch (cron.toLowerCase()) {
            case "hourly" -> lastExec == null
                    || ChronoUnit.MINUTES.between(lastExec, now) >= 60;
            case "daily" -> lastExec == null
                    || ChronoUnit.HOURS.between(lastExec, now) >= 24;
            case "weekly" -> lastExec == null
                    || ChronoUnit.DAYS.between(lastExec, now) >= 7;
            default -> evaluateCustomCron(cron, lastExec, now);
        };
    }

    /**
     * 简化的 cron 评估：对自定义 cron，按最小间隔 1 小时控制频率。
     * 生产环境可替换为 Quartz CronExpression 解析。
     */
    private boolean evaluateCustomCron(String cron, LocalDateTime lastExec, LocalDateTime now) {
        // 自定义 cron 格式，至少间隔 1 小时才执行
        if (lastExec == null) return true;
        return ChronoUnit.MINUTES.between(lastExec, now) >= 60;
    }

    // ============ 工具方法 ============

    private String textOf(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }

    private WorkflowRuleExecutionLogVO toLogVO(WorkflowRuleExecutionLog logEntry) {
        WorkflowRuleExecutionLogVO vo = new WorkflowRuleExecutionLogVO();
        vo.setId(String.valueOf(logEntry.getId()));
        vo.setRuleId(String.valueOf(logEntry.getRuleId()));
        vo.setExecutedAt(logEntry.getExecutedAt());
        vo.setMatchedCount(logEntry.getMatchedCount());
        vo.setSuccessCount(logEntry.getSuccessCount());
        vo.setFailureCount(logEntry.getFailureCount());
        vo.setErrorMessage(logEntry.getErrorMessage());
        vo.setDurationMs(logEntry.getDurationMs());
        return vo;
    }
}

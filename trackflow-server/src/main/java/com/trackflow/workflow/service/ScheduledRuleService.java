package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.service.DistributedLockService;
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
    private final DistributedLockService distributedLockService;

    /**
     * 每分钟执行一次调度检查。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void checkAndExecuteScheduledRules() {
        distributedLockService.executeWithLock("workflow_scheduled_rules", this::doCheckAndExecuteScheduledRules);
    }

    private void doCheckAndExecuteScheduledRules() {
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
    @Transactional(rollbackFor = Exception.class)
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

    @Transactional(rollbackFor = Exception.class)
    protected WorkflowRuleExecutionLogVO executeRule(WorkflowRule rule, LocalDateTime now) {
        long startTime = System.currentTimeMillis();
        int matched = 0, success = 0, failure = 0;
        String errorMessage = null;

        try {
            // 1. 查询匹配工单
            long queryStart = System.currentTimeMillis();
            List<Issue> matchedIssues = findMatchingIssues(rule);
            long queryDuration = System.currentTimeMillis() - queryStart;
            matched = matchedIssues.size();

            log.debug("[ScheduledRule] 规则 '{}' 查询匹配工单: 匹配={}, 查询耗时={}ms",
                    rule.getName(), matched, queryDuration);

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

    /** 单批最大查询量，避免一次性加载过多工单到内存 */
    private static final int BATCH_SIZE = 500;

    private List<Issue> findMatchingIssues(WorkflowRule rule) {
        // 解析条件 JSON
        String condJson = rule.getConditionJson();
        JsonNode conditions = null;
        if (condJson != null && !condJson.isBlank() && !"[]".equals(condJson.trim())) {
            try {
                conditions = objectMapper.readTree(condJson);
                if (!conditions.isArray() || conditions.isEmpty()) {
                    conditions = null;
                }
            } catch (Exception e) {
                log.warn("[ScheduledRule] 条件解析失败: {}", e.getMessage());
                return List.of();
            }
        }

        // 分类条件：可下推到 SQL 的 vs 只能 Java 层处理的
        List<JsonNode> javaOnlyConditions = new ArrayList<>();
        if (conditions != null) {
            LambdaQueryWrapper<Issue> probe = new LambdaQueryWrapper<>();
            for (JsonNode cond : conditions) {
                if (!pushConditionToSql(cond, probe)) {
                    javaOnlyConditions.add(cond);
                }
            }
        }

        // 分批查询（基于 ID 游标分页）
        List<Issue> result = new ArrayList<>();
        Long lastId = null;

        while (true) {
            LambdaQueryWrapper<Issue> batchWrapper = buildBatchWrapper(rule, conditions, javaOnlyConditions, lastId);
            batchWrapper.last("LIMIT " + BATCH_SIZE);

            List<Issue> batch = issueMapper.selectList(batchWrapper);
            if (batch.isEmpty()) {
                break;
            }

            // Java 层过滤无法下推的条件
            if (!javaOnlyConditions.isEmpty()) {
                for (Issue issue : batch) {
                    if (evaluateJavaConditions(javaOnlyConditions, issue)) {
                        result.add(issue);
                    }
                }
            } else {
                result.addAll(batch);
            }

            // 更新游标
            lastId = batch.get(batch.size() - 1).getId();

            // 如果本批不满，说明已到末尾
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }

        return result;
    }

    /**
     * 构建每批次的查询 Wrapper（含所有 SQL 下推条件 + 游标）。
     */
    private LambdaQueryWrapper<Issue> buildBatchWrapper(WorkflowRule rule, JsonNode conditions,
                                                        List<JsonNode> javaOnlyConditions, Long lastId) {
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        // MyBatis-Plus 全局配置已自动添加 deleted_at IS NULL 逻辑删除过滤

        if (rule.getProjectId() != null) {
            wrapper.eq(Issue::getProjectId, rule.getProjectId());
        }

        wrapper.select(
                Issue::getId, Issue::getProjectId, Issue::getIssueKey, Issue::getTitle,
                Issue::getIssueType, Issue::getStatusId, Issue::getPriority,
                Issue::getAssigneeId, Issue::getReporterId, Issue::getSprintId,
                Issue::getDueDate
        );

        // 应用下推条件
        if (conditions != null) {
            for (JsonNode cond : conditions) {
                if (!javaOnlyConditions.contains(cond)) {
                    pushConditionToSql(cond, wrapper);
                }
            }
        }

        wrapper.orderByAsc(Issue::getId);
        if (lastId != null) {
            wrapper.gt(Issue::getId, lastId);
        }

        return wrapper;
    }

    /**
     * 尝试将单个条件下推到 SQL WHERE 子句。
     * @return true 如果成功下推（无需 Java 层再评估），false 表示需要 Java 层处理
     */
    private boolean pushConditionToSql(JsonNode cond, LambdaQueryWrapper<Issue> wrapper) {
        String field = textOf(cond, "field");
        String operator = textOf(cond, "operator");
        String value = textOf(cond, "value");
        if (field == null || operator == null) return true; // 无效条件，跳过

        // overdue 和 due_within_days 是特殊操作符，直接处理日期
        if ("overdue".equals(operator)) {
            wrapper.isNotNull(Issue::getDueDate);
            wrapper.lt(Issue::getDueDate, LocalDate.now());
            return true;
        }
        if ("due_within_days".equals(operator)) {
            if (value == null) return true;
            try {
                int days = Integer.parseInt(value);
                LocalDate today = LocalDate.now();
                wrapper.isNotNull(Issue::getDueDate);
                wrapper.ge(Issue::getDueDate, today);
                wrapper.lt(Issue::getDueDate, today.plusDays(days + 1));
                return true;
            } catch (NumberFormatException e) {
                return true; // 无效值，跳过
            }
        }

        // 标准字段条件下推
        return switch (operator) {
            case "equals" -> pushEquals(field, value, wrapper);
            case "not_equals" -> pushNotEquals(field, value, wrapper);
            case "in" -> pushIn(field, value, wrapper);
            case "is_empty" -> pushIsEmpty(field, wrapper);
            case "is_not_empty" -> pushIsNotEmpty(field, wrapper);
            case "contains" -> false; // contains 只能在 Java 层处理（LIKE 对标题可下推，但通用性不高）
            default -> false;
        };
    }

    private boolean pushEquals(String field, String value, LambdaQueryWrapper<Issue> wrapper) {
        if (value == null) return false;
        return switch (field) {
            case "type", "issue_type" -> { wrapper.eq(Issue::getIssueType, value); yield true; }
            case "priority" -> { wrapper.eq(Issue::getPriority, value); yield true; }
            case "status", "status_id" -> { wrapper.eq(Issue::getStatusId, toLong(value)); yield true; }
            case "assignee", "assignee_id" -> { wrapper.eq(Issue::getAssigneeId, toLong(value)); yield true; }
            case "reporter", "reporter_id" -> { wrapper.eq(Issue::getReporterId, toLong(value)); yield true; }
            case "sprint", "sprint_id" -> { wrapper.eq(Issue::getSprintId, toLong(value)); yield true; }
            default -> false;
        };
    }

    private boolean pushNotEquals(String field, String value, LambdaQueryWrapper<Issue> wrapper) {
        if (value == null) return false;
        return switch (field) {
            case "type", "issue_type" -> { wrapper.ne(Issue::getIssueType, value); yield true; }
            case "priority" -> { wrapper.ne(Issue::getPriority, value); yield true; }
            case "status", "status_id" -> { wrapper.ne(Issue::getStatusId, toLong(value)); yield true; }
            case "assignee", "assignee_id" -> { wrapper.ne(Issue::getAssigneeId, toLong(value)); yield true; }
            case "reporter", "reporter_id" -> { wrapper.ne(Issue::getReporterId, toLong(value)); yield true; }
            case "sprint", "sprint_id" -> { wrapper.ne(Issue::getSprintId, toLong(value)); yield true; }
            default -> false;
        };
    }

    private boolean pushIn(String field, String value, LambdaQueryWrapper<Issue> wrapper) {
        if (value == null || value.isBlank()) return false;
        List<String> values = Arrays.asList(value.split(","));
        return switch (field) {
            case "type", "issue_type" -> { wrapper.in(Issue::getIssueType, values); yield true; }
            case "priority" -> { wrapper.in(Issue::getPriority, values); yield true; }
            case "status", "status_id" -> {
                List<Long> ids = values.stream().map(this::toLong).filter(Objects::nonNull).toList();
                if (!ids.isEmpty()) wrapper.in(Issue::getStatusId, ids);
                yield true;
            }
            case "assignee", "assignee_id" -> {
                List<Long> ids = values.stream().map(this::toLong).filter(Objects::nonNull).toList();
                if (!ids.isEmpty()) wrapper.in(Issue::getAssigneeId, ids);
                yield true;
            }
            case "reporter", "reporter_id" -> {
                List<Long> ids = values.stream().map(this::toLong).filter(Objects::nonNull).toList();
                if (!ids.isEmpty()) wrapper.in(Issue::getReporterId, ids);
                yield true;
            }
            case "sprint", "sprint_id" -> {
                List<Long> ids = values.stream().map(this::toLong).filter(Objects::nonNull).toList();
                if (!ids.isEmpty()) wrapper.in(Issue::getSprintId, ids);
                yield true;
            }
            default -> false;
        };
    }

    private boolean pushIsEmpty(String field, LambdaQueryWrapper<Issue> wrapper) {
        return switch (field) {
            case "assignee", "assignee_id" -> { wrapper.isNull(Issue::getAssigneeId); yield true; }
            case "reporter", "reporter_id" -> { wrapper.isNull(Issue::getReporterId); yield true; }
            case "sprint", "sprint_id" -> { wrapper.isNull(Issue::getSprintId); yield true; }
            case "type", "issue_type" -> { wrapper.and(w -> w.isNull(Issue::getIssueType).or().eq(Issue::getIssueType, "")); yield true; }
            case "priority" -> { wrapper.and(w -> w.isNull(Issue::getPriority).or().eq(Issue::getPriority, "")); yield true; }
            default -> false;
        };
    }

    private boolean pushIsNotEmpty(String field, LambdaQueryWrapper<Issue> wrapper) {
        return switch (field) {
            case "assignee", "assignee_id" -> { wrapper.isNotNull(Issue::getAssigneeId); yield true; }
            case "reporter", "reporter_id" -> { wrapper.isNotNull(Issue::getReporterId); yield true; }
            case "sprint", "sprint_id" -> { wrapper.isNotNull(Issue::getSprintId); yield true; }
            case "type", "issue_type" -> { wrapper.isNotNull(Issue::getIssueType).ne(Issue::getIssueType, ""); yield true; }
            case "priority" -> { wrapper.isNotNull(Issue::getPriority).ne(Issue::getPriority, ""); yield true; }
            default -> false;
        };
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Java 层评估无法下推到 SQL 的条件（如 contains 操作符）。
     */
    private boolean evaluateJavaConditions(List<JsonNode> conditions, Issue issue) {
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

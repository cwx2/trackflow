package com.trackflow.rule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.rule.entity.RuleDefinition;
import com.trackflow.rule.mapper.RuleExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 规则分数计算引擎。
 * 根据 score_formula + score_config 计算每次执行的分数/金额。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private final RuleExecutionLogMapper logMapper;
    private final ObjectMapper objectMapper;

    /**
     * 计算分数
     *
     * @param rule  规则定义
     * @param issue 命中的工单
     * @return 计算结果（score + scoreDetail JSON）
     */
    public ScoreResult calculate(RuleDefinition rule, Issue issue) {
        return switch (rule.getScoreFormula()) {
            case "linear_daily" -> calculateLinearDaily(rule, issue);
            case "fixed" -> calculateFixed(rule);
            case "cumulative_increment" -> calculateCumulativeIncrement(rule, issue);
            case "custom" -> calculateCustom(rule, issue);
            default -> {
                log.warn("[ScoreCalc] 未知公式类型: {}", rule.getScoreFormula());
                yield new ScoreResult(BigDecimal.ZERO, "{}");
            }
        };
    }

    /**
     * 按天线性递增: ceil(overdue_days) * base_amount
     */
    private ScoreResult calculateLinearDaily(RuleDefinition rule, Issue issue) {
        try {
            Map<String, Object> config = parseConfig(rule.getScoreConfig());
            int baseAmount = getIntValue(config, "base_amount", 5);
            LocalDate dueDate = issue.getDueDate();

            if (dueDate == null) {
                return new ScoreResult(BigDecimal.ZERO, "{\"error\": \"no_due_date\"}");
            }

            long overdueDays = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
            if (overdueDays <= 0) {
                return new ScoreResult(BigDecimal.ZERO, "{\"overdue_days\": 0}");
            }

            // 向上取整（不满 1 天按 1 天算）
            BigDecimal score = BigDecimal.valueOf(overdueDays).multiply(BigDecimal.valueOf(baseAmount));

            Map<String, Object> detail = new HashMap<>();
            detail.put("overdue_days", overdueDays);
            detail.put("base_amount", baseAmount);
            detail.put("formula", overdueDays + " × " + baseAmount);

            return new ScoreResult(score, toJson(detail));
        } catch (Exception e) {
            log.error("[ScoreCalc] linear_daily 计算失败: rule={}", rule.getId(), e);
            return new ScoreResult(BigDecimal.ZERO, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 固定金额: 每次命中固定值
     */
    private ScoreResult calculateFixed(RuleDefinition rule) {
        try {
            Map<String, Object> config = parseConfig(rule.getScoreConfig());
            int amount = getIntValue(config, "amount", 0);
            BigDecimal score = BigDecimal.valueOf(amount);

            Map<String, Object> detail = new HashMap<>();
            detail.put("type", "fixed");
            detail.put("amount", amount);

            return new ScoreResult(score, toJson(detail));
        } catch (Exception e) {
            log.error("[ScoreCalc] fixed 计算失败: rule={}", rule.getId(), e);
            return new ScoreResult(BigDecimal.ZERO, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 累计递增: 按累计次数递增金额
     */
    private ScoreResult calculateCumulativeIncrement(RuleDefinition rule, Issue issue) {
        try {
            Map<String, Object> config = parseConfig(rule.getScoreConfig());
            // 获取目标用户累计次数
            Long targetUserId = resolveTargetUserId(rule, issue);
            int totalCount = logMapper.countByRuleAndUser(rule.getId(), targetUserId);

            // amounts 数组，如 [50, 100, 200]
            var amounts = config.get("amounts");
            int[] amountArray;
            if (amounts instanceof java.util.List<?> list) {
                amountArray = list.stream().mapToInt(v -> ((Number) v).intValue()).toArray();
            } else {
                amountArray = new int[]{50, 100, 200};
            }

            int index = Math.min(totalCount, amountArray.length - 1);
            int amount = amountArray[index];
            BigDecimal score = BigDecimal.valueOf(amount);

            Map<String, Object> detail = new HashMap<>();
            detail.put("type", "cumulative_increment");
            detail.put("occurrence", totalCount + 1);
            detail.put("amount", amount);
            detail.put("amounts", amountArray);

            return new ScoreResult(score, toJson(detail));
        } catch (Exception e) {
            log.error("[ScoreCalc] cumulative_increment 计算失败: rule={}", rule.getId(), e);
            return new ScoreResult(BigDecimal.ZERO, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 自定义表达式（简单实现，暂支持固定金额回退）
     */
    private ScoreResult calculateCustom(RuleDefinition rule, Issue issue) {
        try {
            Map<String, Object> config = parseConfig(rule.getScoreConfig());
            // 简单实现：从 config 取 expression 但暂不解析表达式，回退到固定值
            int fallbackAmount = getIntValue(config, "fallback_amount", 0);
            BigDecimal score = BigDecimal.valueOf(fallbackAmount);

            Map<String, Object> detail = new HashMap<>();
            detail.put("type", "custom");
            detail.put("expression", config.getOrDefault("expression", "N/A"));
            detail.put("fallback_amount", fallbackAmount);

            return new ScoreResult(score, toJson(detail));
        } catch (Exception e) {
            log.error("[ScoreCalc] custom 计算失败: rule={}", rule.getId(), e);
            return new ScoreResult(BigDecimal.ZERO, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private Long resolveTargetUserId(RuleDefinition rule, Issue issue) {
        return switch (rule.getTargetField()) {
            case "reporter" -> issue.getReporterId();
            case "created_by" -> issue.getCreatedBy();
            default -> issue.getAssigneeId();
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int getIntValue(Map<String, Object> config, String key, int defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 计算结果封装
     */
    public record ScoreResult(BigDecimal score, String scoreDetail) {}
}

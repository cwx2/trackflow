package com.trackflow.rule.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则统计 VO（排行榜、趋势、汇总）
 */
@Data
public class RuleStatisticsVO {

    /** 排行榜 */
    private List<RankingItem> ranking;

    /** 趋势 */
    private List<TrendItem> trend;

    /** 按规则汇总 */
    private List<RuleSummaryItem> summaryByRule;

    @Data
    public static class RankingItem {
        private String userId;
        private String displayName;
        private BigDecimal totalScore;
        private Long executionCount;
    }

    @Data
    public static class TrendItem {
        private String date;
        private BigDecimal dailyScore;
        private Long dailyCount;
    }

    @Data
    public static class RuleSummaryItem {
        private String ruleId;
        private String ruleName;
        private BigDecimal totalScore;
        private Long executionCount;
    }
}

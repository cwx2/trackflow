package com.trackflow.report.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * 报表配置解析后的 Java 对象
 * 从 report_definition.config (JSONB) 反序列化后进一步解析
 */
@Data
public class ReportConfig {

    private String groupBy;
    private String secondGroupBy;
    private String chartType;
    private String sortBy;
    private ReportFilters filters;
    private ReportTimeRange timeRange;

    /**
     * Issue 自由查询语法筛选条件（JSON 数组字符串，与工单列表/仪表盘使用相同格式）。
     * 通过 QueryExecutor.executeFilterToIds() 解析为 Issue ID 列表来限定报表数据范围。
     * 格式示例：[{"field":"type","operator":"eq","value":["Bug"]},{"field":"priority","operator":"eq","value":["High"]}]
     */
    private String issueFilter;

    /**
     * 自动刷新间隔（秒）。
     * 0 或 null 表示不自动刷新（手动刷新）。
     * 常见值：600（10分钟）、3600（1小时）、86400（每天）。
     */
    private Integer refreshInterval;

    @Data
    public static class ReportFilters {
        private List<String> statuses;
        private List<String> statusesExclude;
        private List<String> priorities;
        private List<String> assignees;
        private List<String> issueTypes;
        private String sprintId;

        // ── 语义快捷筛选（兼容系统预置报表旧格式） ──
        /** true=仅已关闭, false=排除已关闭 */
        private Boolean statusClosed;
        /** true=仅未分配（assignee_id IS NULL） */
        private Boolean unassigned;
        /** true=仅逾期（due_date < today 且未关闭） */
        private Boolean overdue;
        /** true=仅当前活跃 Sprint 的工单 */
        private Boolean activeSprint;
    }

    @Data
    public static class ReportTimeRange {
        /** "dynamic" 或 "fixed" */
        private String type;
        /** 动态预设：this_month / last_month / last_7_days / last_30_days / last_90_days / this_quarter / this_year */
        private String preset;
        /** 针对哪个日期字段：createdAt / updatedAt / resolvedAt（默认 createdAt） */
        private String field;
        /** 固定范围起始日期（type=fixed 时使用） */
        private String startDate;
        /** 固定范围结束日期（type=fixed 时使用） */
        private String endDate;
    }

    /**
     * 从原始 Map 构建 ReportConfig 对象
     */
    @SuppressWarnings("unchecked")
    public static ReportConfig fromMap(Map<String, Object> map) {
        ReportConfig config = new ReportConfig();
        config.setGroupBy((String) map.getOrDefault("groupBy", "status"));
        config.setSecondGroupBy((String) map.get("secondGroupBy"));
        config.setChartType((String) map.get("chartType"));
        config.setSortBy((String) map.get("sortBy"));

        // 解析 issueFilter（自由查询语法字符串）
        Object issueFilterObj = map.get("issueFilter");
        if (issueFilterObj instanceof String s && !s.isBlank()) {
            config.setIssueFilter(s);
        }

        // 解析 filters
        Object filtersObj = map.get("filters");
        if (filtersObj instanceof Map<?, ?> filtersMap) {
            ReportFilters filters = new ReportFilters();
            filters.setStatuses(toStringList(filtersMap.get("statuses")));
            filters.setStatusesExclude(toStringList(filtersMap.get("statusesExclude")));
            filters.setPriorities(toStringList(filtersMap.get("priorities")));
            filters.setAssignees(toStringList(filtersMap.get("assignees")));
            filters.setIssueTypes(toStringList(filtersMap.get("issueTypes")));
            Object sprintId = filtersMap.get("sprintId");
            if (sprintId != null) {
                filters.setSprintId(String.valueOf(sprintId));
            }
            // 语义快捷筛选字段（兼容旧版系统预置报表 config）
            Object statusClosed = filtersMap.get("statusClosed");
            if (statusClosed instanceof Boolean b) {
                filters.setStatusClosed(b);
            }
            Object unassigned = filtersMap.get("unassigned");
            if (unassigned instanceof Boolean b) {
                filters.setUnassigned(b);
            }
            Object overdue = filtersMap.get("overdue");
            if (overdue instanceof Boolean b) {
                filters.setOverdue(b);
            }
            Object activeSprint = filtersMap.get("activeSprint");
            if (activeSprint instanceof Boolean b) {
                filters.setActiveSprint(b);
            }
            config.setFilters(filters);
        }

        // 兼容旧版 periodType → 转换为标准 timeRange
        if (config.getTimeRange() == null && config.getFilters() != null) {
            Object filtersForPeriod = map.get("filters");
            if (filtersForPeriod instanceof Map<?, ?> fm) {
                Object periodType = fm.get("periodType");
                if (periodType instanceof String pt && !pt.isBlank()) {
                    String preset = convertPeriodTypeToPreset(pt);
                    if (preset != null) {
                        ReportTimeRange timeRange = new ReportTimeRange();
                        timeRange.setType("dynamic");
                        timeRange.setPreset(preset);
                        timeRange.setField("createdAt");
                        config.setTimeRange(timeRange);
                    }
                }
            }
        }

        // 解析 timeRange
        Object timeRangeObj = map.get("timeRange");
        if (timeRangeObj instanceof Map<?, ?> timeRangeMap) {
            ReportTimeRange timeRange = new ReportTimeRange();
            timeRange.setType((String) timeRangeMap.get("type"));
            timeRange.setPreset((String) timeRangeMap.get("preset"));
            timeRange.setField((String) timeRangeMap.get("field"));
            timeRange.setStartDate((String) timeRangeMap.get("startDate"));
            timeRange.setEndDate((String) timeRangeMap.get("endDate"));
            config.setTimeRange(timeRange);
        }

        // 解析 refreshInterval
        Object refreshObj = map.get("refreshInterval");
        if (refreshObj instanceof Number num) {
            config.setRefreshInterval(num.intValue());
        }

        return config;
    }

    /**
     * 解析时间范围为 [startDateTime, endDateTime]
     * @return null 如果没配时间范围
     */
    public LocalDateTime[] resolveTimeRange() {
        if (timeRange == null) return null;

        if ("fixed".equals(timeRange.getType())) {
            if (timeRange.getStartDate() == null || timeRange.getEndDate() == null) return null;
            LocalDate start = LocalDate.parse(timeRange.getStartDate());
            LocalDate end = LocalDate.parse(timeRange.getEndDate());
            return new LocalDateTime[]{
                    start.atStartOfDay(),
                    end.atTime(LocalTime.MAX)
            };
        }

        if ("dynamic".equals(timeRange.getType()) && timeRange.getPreset() != null) {
            LocalDate today = LocalDate.now();
            return switch (timeRange.getPreset()) {
                case "this_month" -> new LocalDateTime[]{
                        today.withDayOfMonth(1).atStartOfDay(),
                        today.atTime(LocalTime.MAX)
                };
                case "last_month" -> {
                    LocalDate firstDayLastMonth = today.minusMonths(1).withDayOfMonth(1);
                    LocalDate lastDayLastMonth = firstDayLastMonth.with(TemporalAdjusters.lastDayOfMonth());
                    yield new LocalDateTime[]{
                            firstDayLastMonth.atStartOfDay(),
                            lastDayLastMonth.atTime(LocalTime.MAX)
                    };
                }
                case "last_7_days" -> new LocalDateTime[]{
                        today.minusDays(7).atStartOfDay(),
                        today.atTime(LocalTime.MAX)
                };
                case "last_30_days" -> new LocalDateTime[]{
                        today.minusDays(30).atStartOfDay(),
                        today.atTime(LocalTime.MAX)
                };
                case "last_90_days" -> new LocalDateTime[]{
                        today.minusDays(90).atStartOfDay(),
                        today.atTime(LocalTime.MAX)
                };
                case "this_quarter" -> {
                    int currentMonth = today.getMonthValue();
                    int quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1;
                    LocalDate quarterStart = LocalDate.of(today.getYear(), quarterStartMonth, 1);
                    yield new LocalDateTime[]{
                            quarterStart.atStartOfDay(),
                            today.atTime(LocalTime.MAX)
                    };
                }
                case "this_year" -> new LocalDateTime[]{
                        LocalDate.of(today.getYear(), 1, 1).atStartOfDay(),
                        today.atTime(LocalTime.MAX)
                };
                default -> null;
            };
        }

        return null;
    }

    /**
     * 获取时间范围应用的日期字段名
     * @return SQL 列名（created_at / updated_at / resolved_at）
     */
    public String getTimeField() {
        if (timeRange == null || timeRange.getField() == null) return "created_at";
        return switch (timeRange.getField()) {
            case "updatedAt" -> "updated_at";
            case "resolvedAt" -> "resolved_at";
            default -> "created_at";
        };
    }

    /**
     * 将旧版 periodType 转换为新版 timeRange preset
     */
    private static String convertPeriodTypeToPreset(String periodType) {
        return switch (periodType) {
            case "current_month" -> "this_month";
            case "current_week" -> "last_7_days";
            case "last_month" -> "last_month";
            case "last_week" -> "last_7_days";
            case "current_quarter" -> "this_quarter";
            case "current_year" -> "this_year";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .toList();
        }
        return null;
    }

    /**
     * 判断 filters 是否为空（没有任何有效过滤条件）
     */
    public boolean hasFilters() {
        if (issueFilter != null && !issueFilter.isBlank()) return true;
        if (filters == null) return false;
        return hasContent(filters.getStatuses())
                || hasContent(filters.getStatusesExclude())
                || hasContent(filters.getPriorities())
                || hasContent(filters.getAssignees())
                || hasContent(filters.getIssueTypes())
                || filters.getSprintId() != null
                || filters.getStatusClosed() != null
                || Boolean.TRUE.equals(filters.getUnassigned())
                || Boolean.TRUE.equals(filters.getOverdue())
                || Boolean.TRUE.equals(filters.getActiveSprint());
    }

    private boolean hasContent(List<String> list) {
        return list != null && !list.isEmpty();
    }
}

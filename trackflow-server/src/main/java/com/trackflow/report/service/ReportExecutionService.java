package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.report.dto.ReportQueryParams;
import com.trackflow.report.entity.ReportConfig;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.entity.ReportGroupBy;
import com.trackflow.report.entity.ReportType;
import com.trackflow.report.mapper.ReportDefinitionMapper;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.mapper.result.*;
import com.trackflow.report.vo.ReportExecuteResultVO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.workitemattr.service.WorkItemAttributeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表执行服务 — 负责根据报表类型路由到对应的执行策略并返回结果
 * <p>
 * 核心职责：
 * <ul>
 *   <li>报表类型路由（分布/时间线/状态转换/时间管理）</li>
 *   <li>分布类报表的单维度/双维度执行</li>
 *   <li>查询参数构建（筛选、时间范围、issueFilter 解析）</li>
 *   <li>缓存管理（验证、反序列化、持久化）</li>
 *   <li>项目范围解析</li>
 * </ul>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExecutionService {

    private final ReportDefinitionMapper reportMapper;
    private final ReportStatisticsMapper reportStatisticsMapper;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final StatusCacheHelper statusCacheHelper;
    private final SprintMapper sprintMapper;
    private final CustomFieldService customFieldService;
    private final ReportStatisticsService reportStatisticsService;
    private final QueryExecutor queryExecutor;
    private final WorkItemAttributeService workItemAttributeService;
    private final ReportChartService reportChartService;

    /** 默认缓存 TTL：10 分钟 */
    private static final int DEFAULT_CACHE_TTL_SECONDS = 600;

    /**
     * 执行报表（带缓存策略）
     * <p>
     * 缓存策略（对标 YouTrack）：
     * - 如果缓存未过期且非强制刷新：直接返回缓存结果
     * - 如果缓存过期或强制刷新：重新计算并持久化结果
     *
     * @param report     已验证权限的报表定义
     * @param force      是否强制重新计算（忽略缓存）
     * @param projectIds 项目范围
     */
    public ReportExecuteResultVO execute(ReportDefinition report, boolean force, List<Long> projectIds) {
        // 缓存命中判断（非强制刷新时）
        if (!force && isCacheValid(report)) {
            ReportExecuteResultVO cached = deserializeCachedResult(report.getCachedResult());
            if (cached != null) {
                log.debug("Report cache hit: id={}, lastCalculatedAt={}", report.getId(), report.getLastCalculatedAt());
                return cached;
            }
        }

        // 缓存未命中或强制刷新：执行计算
        ReportExecuteResultVO result = executeInternal(report, projectIds);

        // 持久化计算结果
        persistCachedResult(report, result);

        return result;
    }

    /**
     * 使用 Owner 权限范围执行报表（对标 YouTrack：Report data is retrieved according to
     * the access rights of the report owner），供导出和带权限执行使用。
     */
    public ReportExecuteResultVO executeByOwnerScope(ReportDefinition report) {
        Long executionOwner = report.getCreatedBy();
        List<Long> scopeProjectIds = resolveExecutionScope(report, executionOwner);
        return executeInternal(report, scopeProjectIds);
    }

    /**
     * 执行报表（系统内部调用，无用户权限限制）。
     * 用于定时任务、系统通知等无用户上下文的场景。
     */
    public ReportExecuteResultVO executeNoAuth(ReportDefinition report) {
        List<Long> projectIds = report.getProjectId() != null
                ? List.of(report.getProjectId())
                : null;
        return executeInternal(report, projectIds);
    }

    /**
     * 解析报表执行的项目范围。
     * <p>
     * - 项目级报表：返回 [projectId]
     * - 全局报表 + 系统管理员：返回所有活跃项目 ID（确保 SQL 走索引）
     * - 全局报表 + 普通用户：返回用户可访问的项目 ID 列表
     */
    public List<Long> resolveExecutionScope(ReportDefinition report, Long userId) {
        if (report.getProjectId() != null) {
            return List.of(report.getProjectId());
        }
        List<Long> ids = projectService.getAccessibleProjectIds(userId);
        if (ids == null) {
            ids = projectService.getAllActiveProjectIds();
        }
        if (ids.isEmpty()) {
            return List.of(-1L);
        }
        return ids;
    }

    // ─── 核心执行引擎 ────────────────────────────────────────

    /**
     * 增强版执行引擎入口
     */
    private ReportExecuteResultVO executeInternal(ReportDefinition report, List<Long> projectIds) {
        ReportType reportType = ReportType.fromValue(report.getType());

        // 类型路由
        if (reportType != null && reportType.isTimeline()) {
            return reportChartService.executeTimelineReport(report, reportType, projectIds);
        }
        if (reportType != null && reportType.isStateTransition()) {
            return reportChartService.executeStateTransitionReport(report, projectIds);
        }
        if (reportType != null && reportType.isTimeManagement()) {
            return reportChartService.executeTimeManagementReport(report, reportType, projectIds);
        }

        // 分布类报表
        return executeDistributionReport(report, projectIds);
    }

    /**
     * 执行分布类报表（Issue Distribution 类）
     */
    private ReportExecuteResultVO executeDistributionReport(ReportDefinition report, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        String groupBy = config.getGroupBy();
        if (!ReportGroupBy.isValid(groupBy)) {
            groupBy = "status";
        }

        ReportQueryParams params = buildQueryParams(config, groupBy, projectIds);

        // 自定义字段分组处理
        if (ReportGroupBy.isCustomFieldFormat(groupBy)) {
            Long cfId = ReportGroupBy.extractCustomFieldId(groupBy);
            params.setCustomFieldId(cfId);
            params.setIsCustomFieldGroupBy(true);
            CustomFieldDefinition cfDef = customFieldService.getDefinitionById(cfId);
            if (cfDef != null && "list".equals(cfDef.getFieldFormat())) {
                params.setCustomFieldIsListType(true);
            }
            if (cfDef != null && "user".equals(cfDef.getFieldFormat())) {
                params.setCustomFieldIsUserType(true);
            }
        }

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("distribution");
        result.setGroupBy(groupBy);
        result.setChartType(config.getChartType());

        // 双维度交叉模式
        String secondGroupBy = config.getSecondGroupBy();
        boolean isCrossMode = secondGroupBy != null && !secondGroupBy.isBlank()
                && ReportGroupBy.isValid(secondGroupBy);

        if (isCrossMode) {
            result.setSecondGroupBy(secondGroupBy);
            params.setSecondGroupBy(secondGroupBy);
            if (ReportGroupBy.isCustomFieldFormat(secondGroupBy)) {
                Long cfId2 = ReportGroupBy.extractCustomFieldId(secondGroupBy);
                params.setSecondCustomFieldId(cfId2);
                params.setIsSecondCustomFieldGroupBy(true);
                CustomFieldDefinition cfDef2 = customFieldService.getDefinitionById(cfId2);
                if (cfDef2 != null && "list".equals(cfDef2.getFieldFormat())) {
                    params.setSecondCustomFieldIsListType(true);
                }
                if (cfDef2 != null && "user".equals(cfDef2.getFieldFormat())) {
                    params.setSecondCustomFieldIsUserType(true);
                }
            }
            executeCrossMode(params, result);
        } else {
            executeSingleMode(params, result);
        }

        // 筛选摘要
        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }

        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        return result;
    }

    // ─── 单维度 / 双维度执行 ──────────────────────────────────

    private void executeSingleMode(ReportQueryParams params, ReportExecuteResultVO result) {
        List<ReportGroupRow> rows = reportStatisticsMapper.selectReportGrouped(params);

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long total = 0;

        for (ReportGroupRow row : rows) {
            labels.add(row.getLabel());
            long cnt = row.getCnt() != null ? row.getCnt() : 0L;
            data.add(cnt);
            total += cnt;
        }

        result.setLabels(labels);
        result.setData(data);
        result.setTotal(total);
    }

    private void executeCrossMode(ReportQueryParams params, ReportExecuteResultVO result) {
        List<ReportCrossRow> rows = reportStatisticsMapper.selectReportCross(params);

        LinkedHashSet<String> primarySet = new LinkedHashSet<>();
        LinkedHashSet<String> secondarySet = new LinkedHashSet<>();
        for (ReportCrossRow row : rows) {
            primarySet.add(row.getPrimaryLabel());
            secondarySet.add(row.getSecondaryLabel());
        }

        List<String> primaryLabels = new ArrayList<>(primarySet);
        List<String> secondaryLabels = new ArrayList<>(secondarySet);

        Map<String, Map<String, Long>> lookupMap = new HashMap<>();
        for (ReportCrossRow row : rows) {
            lookupMap.computeIfAbsent(row.getPrimaryLabel(), k -> new HashMap<>())
                    .put(row.getSecondaryLabel(), row.getCnt() != null ? row.getCnt() : 0L);
        }

        List<List<Long>> matrix = new ArrayList<>();
        long total = 0;
        for (String primary : primaryLabels) {
            List<Long> matrixRow = new ArrayList<>();
            Map<String, Long> secondaryMap = lookupMap.getOrDefault(primary, Map.of());
            for (String secondary : secondaryLabels) {
                long cnt = secondaryMap.getOrDefault(secondary, 0L);
                matrixRow.add(cnt);
                total += cnt;
            }
            matrix.add(matrixRow);
        }

        result.setLabels(primaryLabels);
        result.setSecondLabels(secondaryLabels);
        result.setMatrix(matrix);
        result.setTotal(total);
    }

    // ─── 查询参数构建 ────────────────────────────────────────

    /**
     * 构建传给 Mapper 的报表查询参数
     */
    ReportQueryParams buildQueryParams(ReportConfig config, String groupBy, List<Long> projectIds) {
        ReportQueryParams params = new ReportQueryParams();
        params.setGroupBy(groupBy);
        params.setSortBy(config.getSortBy());

        if (projectIds != null) {
            params.setProjectIds(projectIds);
        }

        // 时间范围
        LocalDateTime[] timeRange = config.resolveTimeRange();
        if (timeRange != null) {
            params.setTimeStart(timeRange[0]);
            params.setTimeEnd(timeRange[1]);
            params.setTimeField(config.getTimeField());
        }

        // 筛选条件
        ReportConfig.ReportFilters filters = config.getFilters();
        if (filters != null) {
            applyFilters(filters, params, projectIds);
        }

        // Issue Filter 自由查询语法
        resolveIssueFilterToParams(config, projectIds, params);

        return params;
    }

    private void applyFilters(ReportConfig.ReportFilters filters, ReportQueryParams params, List<Long> projectIds) {
        if (filters.getStatuses() != null && !filters.getStatuses().isEmpty()) {
            params.setStatuses(filters.getStatuses());
        }
        if (filters.getStatusesExclude() != null && !filters.getStatusesExclude().isEmpty()) {
            params.setStatusesExclude(filters.getStatusesExclude());
        }
        if (filters.getPriorities() != null && !filters.getPriorities().isEmpty()) {
            params.setPriorities(filters.getPriorities());
        }
        if (filters.getIssueTypes() != null && !filters.getIssueTypes().isEmpty()) {
            params.setIssueTypes(filters.getIssueTypes());
        }
        if (filters.getSprintId() != null) {
            try {
                params.setSprintId(Long.parseLong(filters.getSprintId()));
            } catch (NumberFormatException e) {
                log.warn("Invalid sprintId in report config: {}", filters.getSprintId());
            }
        }
        if (filters.getAssignees() != null && !filters.getAssignees().isEmpty()) {
            List<Long> assigneeIds = filters.getAssignees().stream()
                    .map(s -> { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } })
                    .filter(Objects::nonNull)
                    .toList();
            if (!assigneeIds.isEmpty()) {
                params.setAssigneeIds(assigneeIds);
            }
        }

        // 语义快捷筛选
        if (filters.getStatusClosed() != null) {
            Set<Long> closedIds = statusCacheHelper.getClosedStatusIds();
            if (!closedIds.isEmpty()) {
                params.setClosedStatusIds(closedIds);
                if (Boolean.TRUE.equals(filters.getStatusClosed())) {
                    params.setOnlyClosedStatus(true);
                } else {
                    params.setExcludeClosedStatus(true);
                }
            }
        }
        if (Boolean.TRUE.equals(filters.getUnassigned())) {
            params.setUnassigned(true);
        }
        if (Boolean.TRUE.equals(filters.getOverdue())) {
            params.setOverdue(true);
            Set<Long> closedIds = statusCacheHelper.getClosedStatusIds();
            if (!closedIds.isEmpty()) {
                params.setClosedStatusIds(closedIds);
            }
        }
        if (Boolean.TRUE.equals(filters.getActiveSprint())) {
            LambdaQueryWrapper<Sprint> sprintQuery = new LambdaQueryWrapper<>();
            sprintQuery.eq(Sprint::getStatus, SprintStatus.ACTIVE);
            if (projectIds != null && !projectIds.isEmpty()) {
                sprintQuery.in(Sprint::getProjectId, projectIds);
            }
            List<Sprint> activeSprints = sprintMapper.selectList(sprintQuery);
            if (!activeSprints.isEmpty()) {
                List<Long> activeSprintIds = activeSprints.stream().map(Sprint::getId).toList();
                params.setActiveSprintIds(activeSprintIds);
            } else {
                params.setSprintId(-1L);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveIssueFilterToParams(ReportConfig config, List<Long> projectIds, ReportQueryParams params) {
        List<Long> issueIds = resolveIssueFilterIds(config, projectIds);
        if (issueIds != null) {
            params.setIssueIds(issueIds);
        }
    }

    /**
     * 解析 config.issueFilter 为 Issue ID 列表。
     * @return 匹配的 Issue ID 列表，null 表示无筛选
     */
    @SuppressWarnings("unchecked")
    List<Long> resolveIssueFilterIds(ReportConfig config, List<Long> projectIds) {
        String issueFilter = config.getIssueFilter();
        if (issueFilter == null || issueFilter.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> filters = objectMapper.readValue(issueFilter,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            if (filters.isEmpty()) {
                return null;
            }
            return queryExecutor.executeFilterToIds(filters, projectIds);
        } catch (Exception e) {
            log.warn("Failed to parse issueFilter in report config: {}", issueFilter, e);
            return null;
        }
    }

    // ─── 缓存管理 ────────────────────────────────────────

    private boolean isCacheValid(ReportDefinition report) {
        if (report.getLastCalculatedAt() == null || report.getCachedResult() == null) {
            return false;
        }
        int ttlSeconds = getEffectiveTtl(report);
        LocalDateTime expireAt = report.getLastCalculatedAt().plusSeconds(ttlSeconds);
        return LocalDateTime.now().isBefore(expireAt);
    }

    private int getEffectiveTtl(ReportDefinition report) {
        Map<String, Object> configMap = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(configMap);
        Integer interval = config.getRefreshInterval();
        return (interval != null && interval > 0) ? interval : DEFAULT_CACHE_TTL_SECONDS;
    }

    private ReportExecuteResultVO deserializeCachedResult(String cachedJson) {
        if (cachedJson == null || cachedJson.isBlank()) return null;
        try {
            return objectMapper.readValue(cachedJson, ReportExecuteResultVO.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize report cached result, will recalculate: {}", e.getMessage());
            return null;
        }
    }

    private void persistCachedResult(ReportDefinition report, ReportExecuteResultVO result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            ReportDefinition update = new ReportDefinition();
            update.setId(report.getId());
            update.setLastCalculatedAt(LocalDateTime.now());
            update.setCachedResult(resultJson);
            reportMapper.updateById(update);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize report result for caching: id={}, error={}", report.getId(), e.getMessage());
        }
    }

    // ─── 工具方法 ────────────────────────────────────────

    Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    Map<String, Object> buildFilterSummary(ReportConfig config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (config.getTimeRange() != null) {
            ReportConfig.ReportTimeRange tr = config.getTimeRange();
            if ("dynamic".equals(tr.getType())) {
                summary.put("timeRange", tr.getPreset());
            } else {
                summary.put("timeRange", tr.getStartDate() + " ~ " + tr.getEndDate());
            }
            summary.put("timeField", tr.getField());
        }
        if (config.getIssueFilter() != null && !config.getIssueFilter().isBlank()) {
            summary.put("issueFilter", config.getIssueFilter());
        }
        ReportConfig.ReportFilters filters = config.getFilters();
        if (filters != null) {
            if (filters.getStatuses() != null) summary.put("statuses", filters.getStatuses());
            if (filters.getStatusesExclude() != null) summary.put("statusesExclude", filters.getStatusesExclude());
            if (filters.getPriorities() != null) summary.put("priorities", filters.getPriorities());
            if (filters.getIssueTypes() != null) summary.put("issueTypes", filters.getIssueTypes());
            if (filters.getAssignees() != null) summary.put("assignees", filters.getAssignees());
            if (filters.getSprintId() != null) summary.put("sprintId", filters.getSprintId());
            if (filters.getStatusClosed() != null) summary.put("statusClosed", filters.getStatusClosed());
            if (Boolean.TRUE.equals(filters.getUnassigned())) summary.put("unassigned", true);
            if (Boolean.TRUE.equals(filters.getOverdue())) summary.put("overdue", true);
            if (Boolean.TRUE.equals(filters.getActiveSprint())) summary.put("activeSprint", true);
        }
        return summary;
    }
}

package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.BoardDataQuery;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.board.vo.BoardDataVO;
import com.trackflow.board.vo.BoardGeneralConfigVO;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.vo.IssueVO;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 看板数据聚合服务 — 提供已按列分组的看板工单数据。
 * <p>
 * 核心优化：前端从循环 N 次 HTTP 请求 → 1 次 HTTP 请求。
 * 服务端完成分组，前端无需客户端 filter。
 * <p>
 * 设计原则：
 * - 复用 IssueService.listWithDetails() 的已有逻辑（填充 user/status/sprint/customField）
 * - 利用大 pageSize（无 500 上限硬编码）一次加载，服务端分组
 * - 折叠列只返回 totalCount 和 totalEstimation，不返回具体工单
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardDataService {

    /** 单列默认最大工单数（超出部分不返回具体数据，仅返回 totalCount） */
    private static final int DEFAULT_COLUMN_LIMIT = 200;

    /** 看板全量最大工单数（安全保护，防止内存溢出） */
    private static final int BOARD_MAX_ISSUES = 2000;

    private final IssueService issueService;
    private final BoardColumnService boardColumnService;
    private final BoardGeneralConfigService boardGeneralConfigService;
    private final SprintMapper sprintMapper;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 聚合看板数据：一次查询返回按列分组的工单。
     * <p>
     * 流程：
     * 1. 获取列配置（确定可见列和折叠列）
     * 2. 应用 Board Behavior 配置（filterMode / filterQuery / doneRetentionDays）
     * 3. 查询可见列的工单（利用 statusId IN 过滤，内部循环分页）
     * 4. 按 statusId 分组，封装为 BoardDataVO
     * 5. 折叠列仅返回统计信息（从列配置中获取 issueCount + totalEstimation）
     *
     * @param query 查询参数
     * @param collapsedStatusIds 前端传递的已折叠列状态 ID 集合（折叠列不返回具体工单）
     * @return 按列分组的看板数据
     */
    public BoardDataVO aggregateBoardData(BoardDataQuery query, Set<Long> collapsedStatusIds) {
        Long projectId = query.getProjectId();

        // 1. 获取列配置（含统计数据）+ Board Behavior 配置
        BoardGeneralConfigVO generalConfig = boardGeneralConfigService.getGeneralConfig(projectId);
        String columnField = generalConfig.getColumnField() != null ? generalConfig.getColumnField() : "status";

        // 2. 应用 Board Behavior：filterMode / doneRetentionDays / filterQuery
        applyBoardBehavior(query, generalConfig);

        List<BoardColumnVO> columnConfigs;
        if ("priority".equals(columnField)) {
            columnConfigs = boardColumnService.getPriorityColumns(projectId);
        } else {
            columnConfigs = boardColumnService.getColumns(projectId);
        }

        // 3. 确定需要加载工单的列（可见 + 非折叠）
        List<BoardColumnVO> visibleColumns = columnConfigs.stream()
                .filter(BoardColumnVO::getVisible)
                .toList();

        // 区分：需要加载工单的列 vs 折叠列（仅需统计）
        List<BoardColumnVO> loadColumns = new ArrayList<>();
        List<BoardColumnVO> collapsedOnlyColumns = new ArrayList<>();

        for (BoardColumnVO col : visibleColumns) {
            Long statusIdLong = parseStatusId(col);
            if (statusIdLong != null && collapsedStatusIds.contains(statusIdLong)) {
                collapsedOnlyColumns.add(col);
            } else {
                loadColumns.add(col);
            }
        }

        // 4. 构建 IssueQuery，使用 statusId IN 过滤仅加载需要的列
        String statusIdFilter = buildStatusIdFilter(loadColumns, columnField);

        // 4.1 如果 filterMode='query'，先获取匹配的 issue IDs 进行交集过滤
        Set<Long> queryFilteredIssueIds = resolveQueryFilterIssueIds(generalConfig, projectId);

        // 内部循环加载所有工单（PageHelper 限制每页 100，这里服务端循环绕过）
        List<IssueVO> allIssues = new ArrayList<>();
        long totalInDb = 0;
        int pageNum = 1;
        final int PAGE_SIZE = 100;

        while (true) {
            IssueQuery issueQuery = buildIssueQuery(query, statusIdFilter, columnField, pageNum, PAGE_SIZE);
            var pageResult = issueService.listWithDetails(issueQuery);
            List<IssueVO> pageIssues = pageResult.getList();
            totalInDb = pageResult.getPagination().getTotal();
            allIssues.addAll(pageIssues);

            // 已加载全部 或 达到安全上限
            if (allIssues.size() >= totalInDb || allIssues.size() >= BOARD_MAX_ISSUES || pageIssues.size() < PAGE_SIZE) {
                break;
            }
            pageNum++;
        }

        // 4.2 如果有 query 过滤结果，做交集
        if (queryFilteredIssueIds != null) {
            allIssues = allIssues.stream()
                    .filter(issue -> {
                        try {
                            Long issueId = Long.parseLong(issue.getId());
                            return queryFilteredIssueIds.contains(issueId);
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }

        // 5. 按列分组
        int columnLimit = query.getColumnLimit() != null && query.getColumnLimit() > 0
                ? query.getColumnLimit() : DEFAULT_COLUMN_LIMIT;

        Map<String, List<IssueVO>> groupedIssues = groupIssuesByColumn(allIssues, columnField);

        // 6. 构建 BoardDataVO
        BoardDataVO result = new BoardDataVO();
        List<BoardDataVO.ColumnData> columnDataList = new ArrayList<>();
        int totalIssueCount = 0;

        for (BoardColumnVO col : visibleColumns) {
            BoardDataVO.ColumnData colData = new BoardDataVO.ColumnData();
            String colKey = getColumnKey(col, columnField);
            colData.setStatusId(col.getStatusId() != null ? col.getStatusId() : col.getFieldValue());
            colData.setStatusName(col.getStatusName());

            Long statusIdLong = parseStatusId(col);
            boolean isCollapsed = statusIdLong != null && collapsedStatusIds.contains(statusIdLong);
            colData.setCollapsed(isCollapsed);

            if (isCollapsed) {
                // 折叠列：使用列配置中的统计数据，不返回具体工单
                colData.setIssues(Collections.emptyList());
                colData.setTotalCount(col.getIssueCount() != null ? col.getIssueCount() : 0);
                colData.setTotalEstimation(col.getTotalEstimation());
            } else {
                // 展开列：返回实际工单（限制每列数量）
                List<IssueVO> columnIssues = groupedIssues.getOrDefault(colKey, Collections.emptyList());
                int columnTotalCount = columnIssues.size();

                if (columnIssues.size() > columnLimit) {
                    colData.setIssues(columnIssues.subList(0, columnLimit));
                } else {
                    colData.setIssues(columnIssues);
                }
                colData.setTotalCount(columnTotalCount);

                // 计算该列 estimation 总和
                BigDecimal estimation = columnIssues.stream()
                        .map(IssueVO::getEstimatedHours)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                colData.setTotalEstimation(estimation.compareTo(BigDecimal.ZERO) > 0 ? estimation : null);
            }

            totalIssueCount += colData.getTotalCount();
            columnDataList.add(colData);
        }

        result.setColumns(columnDataList);
        result.setTotalIssueCount(totalIssueCount);
        result.setTruncated(allIssues.size() < totalInDb);

        return result;
    }

    /**
     * 应用 Board Behavior 配置到查询参数。
     * <p>
     * 服务端强制执行逻辑（对标 YouTrack Board Behavior）：
     * - filterMode='active_sprint'：若前端未传 sprintId，自动附加活跃 Sprint
     * - doneRetentionDays：若前端未传 excludeDoneBefore，服务端按配置计算
     * <p>
     * 优先级：前端显式传入 > 服务端配置 > 不过滤
     */
    private void applyBoardBehavior(BoardDataQuery query, BoardGeneralConfigVO generalConfig) {
        String filterMode = generalConfig.getFilterMode() != null ? generalConfig.getFilterMode() : "all";

        // 1. filterMode='active_sprint'：若前端未传 sprintId，自动查找活跃 Sprint
        if ("active_sprint".equals(filterMode) && query.getSprintId() == null) {
            Sprint activeSprint = sprintMapper.selectOne(
                    new LambdaQueryWrapper<Sprint>()
                            .eq(Sprint::getProjectId, query.getProjectId())
                            .eq(Sprint::getStatus, SprintStatus.ACTIVE)
            );
            if (activeSprint != null) {
                query.setSprintId(activeSprint.getId());
                log.debug("Board Behavior: active_sprint 模式，自动设置 sprintId={}", activeSprint.getId());
            } else {
                // 没有活跃 Sprint 时，设置一个不存在的 ID 使查询返回空
                query.setSprintId(-1L);
                log.debug("Board Behavior: active_sprint 模式，项目无活跃 Sprint，返回空数据");
            }
        }

        // 2. doneRetentionDays：若前端未传 excludeDoneBefore，服务端按配置计算
        if (query.getExcludeDoneBeforeAsDate() == null && generalConfig.getDoneRetentionDays() != null
                && generalConfig.getDoneRetentionDays() > 0) {
            LocalDate cutoff = LocalDate.now().minusDays(generalConfig.getDoneRetentionDays());
            query.setExcludeDoneBeforeDate(cutoff);
            log.debug("Board Behavior: 应用 doneRetentionDays={}，excludeDoneBefore={}",
                    generalConfig.getDoneRetentionDays(), cutoff);
        }
    }

    /**
     * 解析 filterMode='query' 时的查询条件，返回匹配的 issue ID 集合。
     * 如果 filterMode 不是 'query' 或条件为空，返回 null（表示不做 query 交集过滤）。
     */
    @SuppressWarnings("unchecked")
    private Set<Long> resolveQueryFilterIssueIds(BoardGeneralConfigVO generalConfig, Long projectId) {
        String filterMode = generalConfig.getFilterMode() != null ? generalConfig.getFilterMode() : "all";
        if (!"query".equals(filterMode)) {
            return null;
        }

        String filterQueryJson = generalConfig.getFilterQuery();
        if (filterQueryJson == null || filterQueryJson.isBlank()) {
            return null;
        }

        try {
            List<Map<String, Object>> filters = objectMapper.readValue(
                    filterQueryJson, new TypeReference<List<Map<String, Object>>>() {});
            if (filters.isEmpty()) {
                return null;
            }

            // 注入 project 过滤条件（确保只在当前项目内过滤）
            Map<String, Object> projectFilter = new HashMap<>();
            projectFilter.put("field", "project");
            projectFilter.put("operator", "eq");
            projectFilter.put("value", List.of(String.valueOf(projectId)));
            filters.add(0, projectFilter);

            // 使用 QueryExecutor 获取匹配的 issue IDs
            List<Long> matchedIds = queryExecutor.executeFilterToIds(filters, List.of(projectId));
            if (matchedIds == null) {
                return null; // null 表示无筛选
            }
            log.debug("Board Behavior: query 模式匹配 {} 个工单", matchedIds.size());
            return new HashSet<>(matchedIds);
        } catch (Exception e) {
            log.warn("Board Behavior: 解析 filterQuery 失败，跳过 query 过滤: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建 IssueQuery 对象（每页独立构建以避免状态泄漏）
     */
    private IssueQuery buildIssueQuery(BoardDataQuery query, String statusIdFilter, String columnField, int page, int pageSize) {
        IssueQuery issueQuery = new IssueQuery();
        issueQuery.setProjectId(query.getProjectId());
        if (statusIdFilter != null && !statusIdFilter.isEmpty()) {
            if ("priority".equals(columnField)) {
                issueQuery.setPriority(statusIdFilter);
            } else {
                issueQuery.setStatusId(statusIdFilter);
            }
        }
        if (query.getSprintId() != null) {
            issueQuery.setSprintId(String.valueOf(query.getSprintId()));
        }
        if (query.getAssigneeId() != null) {
            issueQuery.setAssigneeId(String.valueOf(query.getAssigneeId()));
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            issueQuery.setKeyword(query.getKeyword());
        }
        if (query.getExcludeDoneBeforeAsDate() != null) {
            issueQuery.setExcludeDoneBefore(query.getExcludeDoneBeforeAsDate());
        }
        issueQuery.setPage(page);
        issueQuery.setPageSize(pageSize);
        issueQuery.setSort("created_at:desc");
        return issueQuery;
    }

    /**
     * 构建状态 ID 过滤字符串（逗号分隔）
     */
    private String buildStatusIdFilter(List<BoardColumnVO> columns, String columnField) {
        if (columns.isEmpty()) return null;

        if ("priority".equals(columnField)) {
            return columns.stream()
                    .map(BoardColumnVO::getFieldValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));
        }

        return columns.stream()
                .map(BoardColumnVO::getStatusId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
    }

    /**
     * 按列分组工单
     */
    private Map<String, List<IssueVO>> groupIssuesByColumn(List<IssueVO> issues, String columnField) {
        if ("priority".equals(columnField)) {
            return issues.stream().collect(Collectors.groupingBy(
                    i -> i.getPriority() != null ? i.getPriority() : "Normal"
            ));
        }
        return issues.stream().collect(Collectors.groupingBy(
                i -> i.getStatusId() != null ? i.getStatusId() : ""
        ));
    }

    /**
     * 获取列的分组 key
     */
    private String getColumnKey(BoardColumnVO col, String columnField) {
        if ("priority".equals(columnField)) {
            return col.getFieldValue() != null ? col.getFieldValue() : "";
        }
        return col.getStatusId() != null ? col.getStatusId() : "";
    }

    /**
     * 解析列的状态 ID（Long 类型）
     */
    private Long parseStatusId(BoardColumnVO col) {
        String idStr = col.getStatusId() != null ? col.getStatusId() : col.getFieldValue();
        if (idStr == null || idStr.isEmpty()) return null;
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

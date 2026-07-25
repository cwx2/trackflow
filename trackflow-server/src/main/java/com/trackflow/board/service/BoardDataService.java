package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.BoardDataQuery;
import com.trackflow.board.vo.BoardCardVO;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.board.vo.BoardDataVO;
import com.trackflow.board.vo.BoardGeneralConfigVO;
import com.trackflow.board.vo.BoardCardConfigVO;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueManualOrderMapper;
import com.trackflow.issue.mapper.result.BoardCardRow;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 看板数据聚合服务 — 提供已按列分组的看板工单数据。
 * <p>
 * 核心优化：
 * - 前端从循环 N 次 HTTP 请求 → 1 次 HTTP 请求（REQ-101）
 * - 服务端从循环分页 + 逐页富化 → 单次 JOIN SQL 查询（REQ-231）
 * <p>
 * 设计原则：
 * - 使用专用 selectBoardCards SQL，一次 JOIN 查出卡片所需全部字段
 * - 自定义字段按需加载（仅卡片配置中 visibleFields 包含的自定义字段）
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

    private final IssueMapper issueMapper;
    private final BoardColumnService boardColumnService;
    private final BoardGeneralConfigService boardGeneralConfigService;
    private final BoardCardConfigService boardCardConfigService;
    private final CustomFieldService customFieldService;
    private final SprintMapper sprintMapper;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;
    private final IssueManualOrderMapper issueManualOrderMapper;

    /**
     * 聚合看板数据：单次查询 + 内存分组，返回按列分组的工单。
     * <p>
     * 流程：
     * 1. 获取列配置（确定可见列和折叠列）
     * 2. 应用 Board Behavior 配置（filterMode / filterQuery / doneRetentionDays）
     * 3. 单次 SQL 查询所有卡片数据（JOIN status + user + sprint）
     * 4. 按需批量加载自定义字段值
     * 5. 按 statusId/priority 分组，封装为 BoardDataVO
     * 6. 折叠列仅返回统计信息
     *
     * @param query 查询参数
     * @param collapsedStatusIds 前端传递的已折叠列状态 ID 集合（折叠列不返回具体工单）
     * @return 按列分组的看板数据
     */
    @Transactional(readOnly = true)
    public BoardDataVO aggregateBoardData(BoardDataQuery query, Set<Long> collapsedStatusIds) {
        Long projectId = query.getProjectId();

        // 1. 获取列配置 + Board Behavior 配置 + 卡片字段配置
        BoardGeneralConfigVO generalConfig = boardGeneralConfigService.getGeneralConfig(projectId);
        String columnField = generalConfig.getColumnField() != null ? generalConfig.getColumnField() : "status";
        BoardCardConfigVO cardConfig = boardCardConfigService.getCardConfig(projectId);

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

        // 4. 构建查询参数并执行单次 SQL
        List<Long> statusIds = null;
        List<String> priorities = null;

        if ("priority".equals(columnField)) {
            priorities = loadColumns.stream()
                    .map(BoardColumnVO::getFieldValue)
                    .filter(Objects::nonNull)
                    .toList();
        } else {
            statusIds = loadColumns.stream()
                    .map(col -> {
                        try {
                            return col.getStatusId() != null ? Long.parseLong(col.getStatusId()) : null;
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }

        Long sprintId = query.getSprintId();
        Long assigneeId = query.getAssigneeId();
        String keyword = query.getKeyword() != null && !query.getKeyword().isBlank() ? query.getKeyword() : null;
        LocalDateTime excludeDoneBefore = query.getExcludeDoneBeforeAsDate() != null
                ? query.getExcludeDoneBeforeAsDate().atStartOfDay() : null;

        // REQ-386: 解析泳道服务端过滤参数
        String swimlaneField = query.getSwimlaneField();
        List<String> swimlaneValues = query.getSwimlaneValues();
        // 泳道过滤仅在字段非空且有选中值时生效
        boolean hasSwimlaneFilter = swimlaneField != null && !swimlaneField.isBlank()
                && !"none".equals(swimlaneField)
                && swimlaneValues != null && !swimlaneValues.isEmpty();

        // REQ-274: 检查项目是否有手动排序，有则在 SQL 层面按手动排序截断
        boolean projectHasManualOrder = hasManualOrder(projectId);
        String manualOrderContextType = projectHasManualOrder ? "project" : null;
        Long manualOrderContextId = projectHasManualOrder ? projectId : null;

        List<BoardCardRow> cardRows = issueMapper.selectBoardCards(
                projectId, statusIds, priorities, sprintId, assigneeId, keyword,
                excludeDoneBefore, BOARD_MAX_ISSUES,
                manualOrderContextType, manualOrderContextId,
                hasSwimlaneFilter ? swimlaneField : null,
                hasSwimlaneFilter ? swimlaneValues : null
        );

        // 4.1 如果 filterMode='query'，获取匹配的 issue IDs 做交集
        Set<Long> queryFilteredIssueIds = resolveQueryFilterIssueIds(generalConfig, projectId);
        if (queryFilteredIssueIds != null) {
            cardRows = cardRows.stream()
                    .filter(row -> queryFilteredIssueIds.contains(row.getId()))
                    .collect(Collectors.toList());
        }

        long totalInDb = cardRows.size();

        // 5. 转换为 BoardCardVO + 按需加载自定义字段
        List<BoardCardVO> allCards = convertToCardVOs(cardRows);
        loadCustomFieldsForCards(allCards, cardRows, cardConfig);

        // 6. 按列分组
        int columnLimit = query.getColumnLimit() != null && query.getColumnLimit() > 0
                ? query.getColumnLimit() : DEFAULT_COLUMN_LIMIT;

        Map<String, List<BoardCardVO>> groupedCards = groupCardsByColumn(allCards, columnField);

        // 7. 构建 BoardDataVO
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
                List<BoardCardVO> columnCards = groupedCards.getOrDefault(colKey, Collections.emptyList());
                int columnTotalCount = columnCards.size();

                if (columnCards.size() > columnLimit) {
                    colData.setIssues(columnCards.subList(0, columnLimit));
                } else {
                    colData.setIssues(columnCards);
                }
                colData.setTotalCount(columnTotalCount);

                // 计算该列 estimation 总和
                BigDecimal estimation = columnCards.stream()
                        .map(BoardCardVO::getEstimatedHours)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                colData.setTotalEstimation(estimation.compareTo(BigDecimal.ZERO) > 0 ? estimation : null);
            }

            totalIssueCount += colData.getTotalCount();
            columnDataList.add(colData);
        }

        result.setColumns(columnDataList);
        result.setColumnConfigs(columnConfigs);
        result.setTotalIssueCount(totalIssueCount);
        result.setTruncated(totalInDb >= BOARD_MAX_ISSUES);

        return result;
    }

    /**
     * 将 BoardCardRow 列表转换为 BoardCardVO 列表。
     * Long ID 字段统一转为 String（防止 JS 精度丢失）。
     */
    private List<BoardCardVO> convertToCardVOs(List<BoardCardRow> rows) {
        List<BoardCardVO> cards = new ArrayList<>(rows.size());
        for (BoardCardRow row : rows) {
            BoardCardVO card = new BoardCardVO();
            card.setId(String.valueOf(row.getId()));
            card.setProjectId(String.valueOf(row.getProjectId()));
            card.setIssueKey(row.getIssueKey());
            card.setTitle(row.getTitle());
            card.setIssueType(row.getIssueType());
            card.setStatusId(String.valueOf(row.getStatusId()));
            card.setStatusName(row.getStatusName());
            card.setStatusColor(row.getStatusColor());
            card.setPriority(row.getPriority());
            card.setAssigneeId(row.getAssigneeId() != null ? String.valueOf(row.getAssigneeId()) : null);
            card.setAssigneeName(row.getAssigneeName());
            card.setAssigneeAvatarUrl(row.getAssigneeAvatarUrl());
            card.setSprintId(row.getSprintId() != null ? String.valueOf(row.getSprintId()) : null);
            card.setSprintName(row.getSprintName());
            card.setDueDate(row.getDueDate());
            card.setEstimatedHours(row.getEstimatedHours());
            card.setChildCount(row.getChildCount());
            card.setChildClosedCount(row.getChildClosedCount());
            card.setCreatedAt(row.getCreatedAt());
            card.setResolvedAt(row.getResolvedAt());
            cards.add(card);
        }
        return cards;
    }

    /**
     * 按需加载自定义字段值。
     * 看板卡片始终加载自定义字段详情（与列表行为一致），
     * 因为卡片模板中会根据 cardConfig.visibleFields 动态展示。
     */
    private void loadCustomFieldsForCards(List<BoardCardVO> cards, List<BoardCardRow> rows, BoardCardConfigVO cardConfig) {
        if (cards.isEmpty()) {
            return;
        }

        // 批量加载所有卡片的自定义字段详情
        List<Long> issueIds = rows.stream().map(BoardCardRow::getId).toList();
        Map<Long, List<CustomFieldValueVO>> cfDetailsMap = customFieldService.getBatchCustomFieldDetails(issueIds);

        for (int i = 0; i < cards.size(); i++) {
            Long issueId = rows.get(i).getId();
            List<CustomFieldValueVO> details = cfDetailsMap.get(issueId);
            if (details != null && !details.isEmpty()) {
                cards.get(i).setCustomFieldDetails(details);
            }
        }
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
     * 按列分组看板卡片
     */
    private Map<String, List<BoardCardVO>> groupCardsByColumn(List<BoardCardVO> cards, String columnField) {
        if ("priority".equals(columnField)) {
            return cards.stream().collect(Collectors.groupingBy(
                    card -> card.getPriority() != null ? card.getPriority() : "Normal"
            ));
        }
        return cards.stream().collect(Collectors.groupingBy(
                card -> card.getStatusId() != null ? card.getStatusId() : ""
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

    /**
     * 判断指定项目是否存在手动排序数据（REQ-274）。
     * <p>
     * 当项目存在手动排序时，看板 SQL 应按手动排序截断，确保排序靠前的卡片不被 LIMIT 丢弃。
     *
     * @param projectId 项目 ID
     * @return 是否存在手动排序数据
     */
    private boolean hasManualOrder(Long projectId) {
        List<Long> issueIds = issueManualOrderMapper.selectIssueIdsByContext("project", projectId, null);
        return issueIds != null && !issueIds.isEmpty();
    }
}

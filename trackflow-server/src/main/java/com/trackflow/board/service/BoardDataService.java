package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.dto.BoardDataQuery;
import com.trackflow.board.vo.BoardCardVO;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.board.vo.BoardDataVO;
import com.trackflow.board.vo.BoardGeneralConfigVO;
import com.trackflow.board.vo.BoardCardConfigVO;
import com.trackflow.common.constant.IssuePriority;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.entity.IssueTagRelation;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueManualOrderMapper;
import com.trackflow.issue.mapper.IssueTagMapper;
import com.trackflow.issue.mapper.IssueTagRelationMapper;
import com.trackflow.issue.mapper.result.BoardCardRow;
import com.trackflow.issue.vo.IssueTagVO;
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
    private final com.trackflow.customfield.mapper.CustomFieldOptionMapper customFieldOptionMapper;
    private final IssueTagRelationMapper issueTagRelationMapper;
    private final IssueTagMapper issueTagMapper;
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

        // 3. 确定需要加载工单的列（可见 + 非折叠，或全部列）
        boolean showAll = Boolean.TRUE.equals(query.getShowAllColumns());
        List<BoardColumnVO> visibleColumns = columnConfigs.stream()
                .filter(col -> showAll || Boolean.TRUE.equals(col.getVisible()))
                .toList();

        // 区分折叠列（用于结果构建时不返回具体工单）
        // REQ-382: 所有可见列都参与 SQL 查询，确保 estimation 基于实际过滤数据

        // 4. 构建查询参数并执行单次 SQL
        // REQ-382: 查询所有可见列（含折叠列）的工单，以正确计算 sprint 过滤后的 estimation
        List<Long> statusIds = null;
        List<String> priorities = null;

        if ("priority".equals(columnField)) {
            priorities = visibleColumns.stream()
                    .map(BoardColumnVO::getFieldValue)
                    .filter(Objects::nonNull)
                    .toList();
        } else {
            statusIds = visibleColumns.stream()
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

        // REQ-602: 检查是否为跨项目看板（有关联项目时使用多项目查询）
        List<String> linkedProjectIdStrs = generalConfig.getLinkedProjectIds();
        boolean isMultiProjectBoard = linkedProjectIdStrs != null && !linkedProjectIdStrs.isEmpty();

        List<BoardCardRow> cardRows;
        if (isMultiProjectBoard) {
            // 多项目看板：合并主项目和关联项目的工单
            List<Long> allProjectIds = new ArrayList<>();
            allProjectIds.add(projectId);
            for (String idStr : linkedProjectIdStrs) {
                try {
                    allProjectIds.add(Long.parseLong(idStr));
                } catch (NumberFormatException e) {
                    log.warn("Board 关联项目 ID 格式无效: {}", idStr);
                }
            }
            cardRows = issueMapper.selectBoardCardsMultiProject(
                    allProjectIds, statusIds, priorities, sprintId, assigneeId, keyword,
                    excludeDoneBefore, BOARD_MAX_ISSUES,
                    hasSwimlaneFilter ? swimlaneField : null,
                    hasSwimlaneFilter ? swimlaneValues : null
            );
        } else {
            // 单项目看板：按项目 ID 查询（支持手动排序）
            // REQ-274: 检查项目是否有手动排序，有则在 SQL 层面按手动排序截断
            boolean projectHasManualOrder = hasManualOrder(projectId);
            String manualOrderContextType = projectHasManualOrder ? "project" : null;
            Long manualOrderContextId = projectHasManualOrder ? projectId : null;

            cardRows = issueMapper.selectBoardCards(
                    projectId, statusIds, priorities, sprintId, assigneeId, keyword,
                    excludeDoneBefore, BOARD_MAX_ISSUES,
                    manualOrderContextType, manualOrderContextId,
                    hasSwimlaneFilter ? swimlaneField : null,
                    hasSwimlaneFilter ? swimlaneValues : null
            );
        }

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

        // 5.1 按需加载标签（当 cardConfig.visibleFields 包含 "tags" 时）
        loadTagsForCards(allCards, cardRows, cardConfig);

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
                // 折叠列：不返回具体工单，但从实际过滤后的卡片计算统计数据
                // REQ-382: 使用 sprint 过滤后的实际卡片计算 estimation，避免使用项目级全量值
                List<BoardCardVO> columnCards = groupedCards.getOrDefault(colKey, Collections.emptyList());
                colData.setIssues(Collections.emptyList());
                colData.setTotalCount(columnCards.size());
                BigDecimal estimation = columnCards.stream()
                        .map(BoardCardVO::getEstimatedHours)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                colData.setTotalEstimation(estimation.compareTo(BigDecimal.ZERO) > 0 ? estimation : null);
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

        // REQ-382: 同步 columnConfigs 的 totalEstimation/issueCount 为实际过滤后的值
        // 前端工具栏从 columnConfigs[].totalEstimation 计算看板总预估工时
        Map<String, BoardDataVO.ColumnData> colDataMap = new HashMap<>();
        for (BoardDataVO.ColumnData cd : columnDataList) {
            if (cd.getStatusId() != null) {
                colDataMap.put(cd.getStatusId(), cd);
            }
        }
        for (BoardColumnVO cfg : columnConfigs) {
            String cfgKey = cfg.getStatusId() != null ? cfg.getStatusId() : cfg.getFieldValue();
            BoardDataVO.ColumnData actualCol = colDataMap.get(cfgKey);
            if (actualCol != null) {
                cfg.setTotalEstimation(actualCol.getTotalEstimation());
                cfg.setIssueCount(actualCol.getTotalCount());
            } else {
                // 列不在可见范围内（hidden），estimation 清零
                cfg.setTotalEstimation(null);
                cfg.setIssueCount(0);
            }
        }

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

        // Collect option IDs for batch query
        Set<Long> optionIds = new HashSet<>();
        for (BoardCardRow row : rows) {
            if (row.getPriorityOptionId() != null) optionIds.add(row.getPriorityOptionId());
            if (row.getIssueTypeOptionId() != null) optionIds.add(row.getIssueTypeOptionId());
        }
        Map<Long, com.trackflow.customfield.entity.CustomFieldOption> optionMap = Collections.emptyMap();
        if (!optionIds.isEmpty()) {
            optionMap = customFieldOptionMapper.selectBatchIds(optionIds).stream()
                    .collect(Collectors.toMap(com.trackflow.customfield.entity.CustomFieldOption::getId, o -> o, (a, b) -> a));
        }

        for (BoardCardRow row : rows) {
            BoardCardVO card = new BoardCardVO();
            card.setId(String.valueOf(row.getId()));
            card.setProjectId(String.valueOf(row.getProjectId()));
            card.setProjectKey(row.getProjectKey());
            card.setIssueKey(row.getIssueKey());
            card.setTitle(row.getTitle());
            card.setIssueType(row.getIssueType());
            card.setStatusId(String.valueOf(row.getStatusId()));
            card.setStatusName(row.getStatusName());
            card.setStatusColor(row.getStatusColor());
            card.setPriority(row.getPriority());
            // Fill priority/type colors from option table
            if (row.getPriorityOptionId() != null) {
                com.trackflow.customfield.entity.CustomFieldOption pOpt = optionMap.get(row.getPriorityOptionId());
                if (pOpt != null) {
                    card.setPriority(pOpt.getValue());
                    card.setPriorityColor(pOpt.getColor());
                }
            }
            if (row.getIssueTypeOptionId() != null) {
                com.trackflow.customfield.entity.CustomFieldOption tOpt = optionMap.get(row.getIssueTypeOptionId());
                if (tOpt != null) {
                    card.setIssueType(tOpt.getValue());
                    card.setIssueTypeColor(tOpt.getColor());
                }
            }
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
            // 父工单信息（用于 Issues 类型泳道分组）
            card.setParentId(row.getParentId() != null ? String.valueOf(row.getParentId()) : null);
            card.setParentIssueKey(row.getParentIssueKey());
            card.setParentTitle(row.getParentTitle());
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
     * 按需加载标签。
     * 当 cardConfig.visibleFields 包含 "tags" 时，批量查询工单-标签关联关系，
     * 然后批量加载标签详情，填充到 BoardCardVO.tags 中。
     * <p>
     * 性能：使用 IN 查询避免 N+1 问题，两次 SQL 即可完成所有标签加载。
     */
    private void loadTagsForCards(List<BoardCardVO> cards, List<BoardCardRow> rows, BoardCardConfigVO cardConfig) {
        if (cards.isEmpty()) {
            return;
        }

        // 仅当卡片配置了显示 tags 字段时才加载
        List<String> visibleFields = cardConfig.getVisibleFields();
        if (visibleFields == null || !visibleFields.contains("tags")) {
            return;
        }

        // 批量查询所有工单的标签关联
        List<Long> issueIds = rows.stream().map(BoardCardRow::getId).toList();
        List<IssueTagRelation> relations = issueTagRelationMapper.selectList(
                new LambdaQueryWrapper<IssueTagRelation>()
                        .in(IssueTagRelation::getIssueId, issueIds)
        );

        if (relations.isEmpty()) {
            return;
        }

        // 收集所有 tagId 并批量查询标签详情
        List<Long> tagIds = relations.stream()
                .map(IssueTagRelation::getTagId)
                .distinct()
                .toList();

        List<IssueTag> tags = issueTagMapper.selectBatchIds(tagIds);
        Map<Long, IssueTag> tagMap = tags.stream()
                .collect(Collectors.toMap(IssueTag::getId, t -> t));

        // 按 issueId 分组关联关系
        Map<Long, List<IssueTagRelation>> relsByIssue = relations.stream()
                .collect(Collectors.groupingBy(IssueTagRelation::getIssueId));

        // 填充每张卡片的标签列表
        for (int i = 0; i < cards.size(); i++) {
            Long issueId = rows.get(i).getId();
            List<IssueTagRelation> issueRels = relsByIssue.get(issueId);
            if (issueRels != null && !issueRels.isEmpty()) {
                List<IssueTagVO> tagVOs = issueRels.stream()
                        .map(rel -> tagMap.get(rel.getTagId()))
                        .filter(Objects::nonNull)
                        .map(tag -> {
                            IssueTagVO vo = new IssueTagVO();
                            vo.setId(String.valueOf(tag.getId()));
                            vo.setName(tag.getName());
                            vo.setColor(tag.getColor());
                            return vo;
                        })
                        .toList();
                cards.get(i).setTags(tagVOs);
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
                    card -> card.getPriority() != null ? card.getPriority() : IssuePriority.DEFAULT.getValue()
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

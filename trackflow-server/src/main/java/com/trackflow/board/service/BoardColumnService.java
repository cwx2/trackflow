package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.board.dto.UpdateBoardColumnsDTO;
import com.trackflow.board.entity.BoardColumnConfig;
import com.trackflow.board.mapper.BoardColumnConfigMapper;
import com.trackflow.board.vo.BoardColumnVO;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardColumnService {

    /** 基础工作流状态 code 集合（V5__issue_schema.sql 中定义的种子状态） */
    private static final Set<String> SEED_STATUS_CODES = Set.of(
            "open", "in_progress", "code_review", "testing", "done", "cancelled"
    );

    /** Redis 缓存前缀 */
    private static final String CACHE_PREFIX = "board:columns:";
    /** Redis 缓存 TTL（秒） */
    private static final long CACHE_TTL_SECONDS = 30;

    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueMapper issueMapper;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 获取项目的看板列配置（纯读取，不执行任何写操作）。
     * <p>
     * 使用 Redis 短期缓存（30s TTL），避免高频读取时重复查询数据库。
     * 如果项目没有持久化配置，基于默认规则在内存中计算列视图返回。
     * 如果隐藏列中有工单，通过 {@code hasHiddenIssues} 标记告知前端，
     * 而不是自动修改配置。
     */
    public List<BoardColumnVO> getColumns(Long projectId) {
        // 1. 尝试从缓存获取
        List<BoardColumnVO> cached = getFromCache(projectId);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，执行数据库查询（已优化为 3 次查询）
        List<BoardColumnVO> result = loadColumnsFromDb(projectId);

        // 3. 写入缓存
        putToCache(projectId, result);

        return result;
    }

    /**
     * 从数据库加载列配置数据（优化为 3 次查询）。
     * <p>
     * 查询①：所有状态定义（issue_status 表，全局数据，变化极少）
     * 查询②：项目列配置（board_column_config 表）
     * 查询③：合并的状态统计 + 工作流状态（CTE 聚合，原 3 条 SQL 合为 1 条）
     */
    private List<BoardColumnVO> loadColumnsFromDb(Long projectId) {
        // 查询①：获取所有状态（排序）
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder)
        );

        // 查询②：获取项目的持久化配置
        List<BoardColumnConfig> configs = boardColumnConfigMapper.selectList(
                new LambdaQueryWrapper<BoardColumnConfig>()
                        .eq(BoardColumnConfig::getProjectId, projectId)
                        .orderByAsc(BoardColumnConfig::getSortOrder)
        );

        // 查询③：合并查询——状态工单计数 + 工作流状态（替代原 3 次独立查询）
        List<Map<String, Object>> aggregatedRows = issueMapper.selectBoardColumnAggregation(projectId);
        Map<Long, Integer> issueCountMap = new HashMap<>();
        Set<Long> usedStatusIds = new HashSet<>();
        Set<Long> workflowStatusIds = new HashSet<>();
        parseAggregatedRows(aggregatedRows, issueCountMap, usedStatusIds, workflowStatusIds);

        // 构建结果
        if (configs.isEmpty()) {
            return buildDefaultView(allStatuses, usedStatusIds, issueCountMap, workflowStatusIds);
        }
        return buildConfiguredView(allStatuses, configs, usedStatusIds, issueCountMap, workflowStatusIds);
    }

    /**
     * 解析合并查询的结果行，填充 issueCountMap、usedStatusIds、workflowStatusIds。
     * <p>
     * 每行包含: status_id, issue_count（可为 0）, in_workflow（boolean）
     */
    private void parseAggregatedRows(List<Map<String, Object>> rows,
                                     Map<Long, Integer> issueCountMap,
                                     Set<Long> usedStatusIds,
                                     Set<Long> workflowStatusIds) {
        if (rows == null || rows.isEmpty()) return;
        for (Map<String, Object> row : rows) {
            Long statusId = ((Number) row.get("status_id")).longValue();
            int issueCount = ((Number) row.get("issue_count")).intValue();
            boolean inWorkflow = (Boolean) row.get("in_workflow");

            if (issueCount > 0) {
                issueCountMap.put(statusId, issueCount);
                usedStatusIds.add(statusId);
            }
            if (inWorkflow) {
                workflowStatusIds.add(statusId);
            }
        }
    }

    /**
     * 显式初始化项目的看板列配置。
     * <p>
     * 仅在以下场景调用：
     * 1. 用户首次打开看板设置面板
     * 2. 项目创建后的默认初始化
     * <p>
     * 如果项目已有配置，此方法不做任何操作（幂等）。
     * <p>
     * 智能推荐逻辑（优先级从高到低）：
     * 1. 项目已有工单使用的状态 → 必须可见
     * 2. 核心工作流路径上的种子状态（Open→In Progress→Code Review→Testing→Done→Cancelled）→ 默认可见
     * 3. 其他在工作流中存在但无工单的状态 → 默认不可见
     *
     * @return 初始化后的列配置
     */
    @Transactional
    public List<BoardColumnVO> initializeColumns(Long projectId) {
        // 检查是否已有配置（幂等保护）
        Long existingCount = boardColumnConfigMapper.selectCount(
                new LambdaQueryWrapper<BoardColumnConfig>()
                        .eq(BoardColumnConfig::getProjectId, projectId)
        );
        if (existingCount > 0) {
            // 已有配置，直接返回现有结果
            return getColumns(projectId);
        }

        // 获取所有状态
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder)
        );

        // 将 code 映射为 ID
        Set<Long> seedStatusIds = allStatuses.stream()
                .filter(s -> SEED_STATUS_CODES.contains(s.getCode()))
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());

        // 查询该项目已有工单涉及的状态 ID
        Set<Long> usedStatusIds = getProjectUsedStatusIds(projectId);

        // 查询项目工作流中涉及的状态 ID
        Set<Long> workflowStatusIds = getProjectWorkflowStatusIds(projectId);

        // 智能推荐：种子状态 + 项目使用中的状态 可见
        // 如果项目有工单使用了非种子状态，也应该显示
        Set<Long> recommendedVisible = new HashSet<>(seedStatusIds);
        recommendedVisible.addAll(usedStatusIds);

        // 构建并持久化配置
        LocalDateTime now = LocalDateTime.now();
        List<BoardColumnConfig> configs = new ArrayList<>();
        int order = 0;
        for (IssueStatus status : allStatuses) {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(status.getId());
            config.setVisible(recommendedVisible.contains(status.getId()));
            config.setSortOrder(order);
            config.setCollapsed(false);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            configs.add(config);
            order++;
        }

        Db.saveBatch(configs);

        // 查询工单数量用于返回
        Map<Long, Integer> issueCountMap = getProjectIssueCountByStatus(projectId);

        // 返回初始化后的视图
        return buildConfiguredView(allStatuses, configs, usedStatusIds, issueCountMap, workflowStatusIds);
    }

    /**
     * 保存项目的看板列配置。
     * <p>
     * 执行完整的业务校验：
     * 1. 所有 statusId 必须存在于 issue_status 表
     * 2. 至少有一个列为 visible=true
     * 3. WIP 限制值必须非负，且 wipMin <= wipMax
     * 4. 未在提交数据中出现的状态自动补全为 visible=false
     */
    @Transactional
    public void saveColumns(Long projectId, UpdateBoardColumnsDTO dto) {
        List<UpdateBoardColumnsDTO.ColumnItem> items = dto.getColumns();

        // ===== 业务校验 =====

        // 1. 验证所有 statusId 存在于 issue_status 表
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(
                Wrappers.<IssueStatus>lambdaQuery().orderByAsc(IssueStatus::getSortOrder)
        );
        Set<Long> validStatusIds = allStatuses.stream()
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());

        for (UpdateBoardColumnsDTO.ColumnItem item : items) {
            if (!validStatusIds.contains(item.getStatusId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "无效的状态 ID: " + item.getStatusId());
            }
        }

        // 2. 验证至少有一个列为可见
        boolean hasVisible = items.stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getVisible()));
        if (!hasVisible) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要一个可见的状态列");
        }

        // 3. 验证 WIP 限制逻辑
        for (UpdateBoardColumnsDTO.ColumnItem item : items) {
            if (item.getWipMin() != null && item.getWipMin() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "WIP 最小值不能为负数");
            }
            if (item.getWipMax() != null && item.getWipMax() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "WIP 最大值不能为负数");
            }
            if (item.getWipMin() != null && item.getWipMax() != null
                    && item.getWipMin() > item.getWipMax()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "WIP 最小值不能大于最大值");
            }
        }

        // 4. 验证 statusId 无重复
        Set<Long> uniqueStatusIds = new HashSet<>();
        for (UpdateBoardColumnsDTO.ColumnItem item : items) {
            if (!uniqueStatusIds.add(item.getStatusId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "列配置中存在重复的状态 ID: " + item.getStatusId());
            }
        }

        // ===== 补全缺失状态 =====

        // 收集前端已提交的 statusId
        Set<Long> providedIds = items.stream()
                .map(UpdateBoardColumnsDTO.ColumnItem::getStatusId)
                .collect(Collectors.toSet());

        // 计算最大 sortOrder，用于追加缺失状态
        int maxSortOrder = items.stream()
                .mapToInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0)
                .max()
                .orElse(0);

        // 对未提交的状态自动补全为 visible=false
        List<UpdateBoardColumnsDTO.ColumnItem> supplemented = new ArrayList<>(items);
        for (IssueStatus status : allStatuses) {
            if (!providedIds.contains(status.getId())) {
                UpdateBoardColumnsDTO.ColumnItem missing = new UpdateBoardColumnsDTO.ColumnItem();
                missing.setStatusId(status.getId());
                missing.setVisible(false);
                missing.setSortOrder(++maxSortOrder);
                missing.setCollapsed(false);
                supplemented.add(missing);
            }
        }

        // ===== 持久化 =====

        // 删除旧配置
        boardColumnConfigMapper.delete(
                new LambdaQueryWrapper<BoardColumnConfig>()
                        .eq(BoardColumnConfig::getProjectId, projectId)
        );

        // 批量构建新配置实体
        LocalDateTime now = LocalDateTime.now();
        List<BoardColumnConfig> configs = new ArrayList<>();
        int order = 0;
        for (UpdateBoardColumnsDTO.ColumnItem item : supplemented) {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(item.getStatusId());
            config.setVisible(item.getVisible());
            config.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            config.setCollapsed(item.getCollapsed() != null ? item.getCollapsed() : false);
            config.setWipMin(item.getWipMin());
            config.setWipMax(item.getWipMax());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            configs.add(config);
            order++;
        }

        Db.saveBatch(configs);
    }

    // ========== Private methods ==========

    /**
     * 无持久化配置时，基于默认规则在内存中计算列视图。
     * 不执行任何写操作。
     */
    private List<BoardColumnVO> buildDefaultView(List<IssueStatus> allStatuses, Set<Long> usedStatusIds,
                                                  Map<Long, Integer> issueCountMap, Set<Long> workflowStatusIds) {
        Set<Long> seedStatusIds = allStatuses.stream()
                .filter(s -> SEED_STATUS_CODES.contains(s.getCode()))
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());

        List<BoardColumnVO> result = new ArrayList<>();
        int order = 0;
        for (IssueStatus status : allStatuses) {
            BoardColumnVO vo = new BoardColumnVO();
            vo.setStatusId(String.valueOf(status.getId()));
            vo.setStatusName(status.getName());
            vo.setStatusCode(status.getCode());
            vo.setStatusColor(status.getColor());
            vo.setStatusCategory(status.getCategory());
            // 默认可见规则：基础状态或有工单使用
            boolean visible = seedStatusIds.contains(status.getId()) || usedStatusIds.contains(status.getId());
            vo.setVisible(visible);
            vo.setSortOrder(order);
            vo.setCollapsed(false);
            // 标记隐藏列中是否有工单
            vo.setHasHiddenIssues(!visible && usedStatusIds.contains(status.getId()));
            // 新增字段
            vo.setIssueCount(issueCountMap.getOrDefault(status.getId(), 0));
            vo.setInWorkflow(workflowStatusIds.contains(status.getId()));
            result.add(vo);
            order++;
        }
        return result;
    }

    /**
     * 有持久化配置时，按配置构建视图，同时标注隐藏列的工单存在情况。
     * 不执行任何写操作。
     */
    private List<BoardColumnVO> buildConfiguredView(
            List<IssueStatus> allStatuses,
            List<BoardColumnConfig> configs,
            Set<Long> usedStatusIds,
            Map<Long, Integer> issueCountMap,
            Set<Long> workflowStatusIds) {

        Map<Long, BoardColumnConfig> configMap = configs.stream()
                .collect(Collectors.toMap(BoardColumnConfig::getStatusId, c -> c));

        List<BoardColumnVO> result = new ArrayList<>();
        for (IssueStatus status : allStatuses) {
            BoardColumnConfig config = configMap.get(status.getId());
            BoardColumnVO vo = new BoardColumnVO();
            vo.setStatusId(String.valueOf(status.getId()));
            vo.setStatusName(status.getName());
            vo.setStatusCode(status.getCode());
            vo.setStatusColor(status.getColor());
            vo.setStatusCategory(status.getCategory());

            if (config != null) {
                vo.setVisible(config.getVisible());
                vo.setSortOrder(config.getSortOrder());
                vo.setCollapsed(config.getCollapsed());
                vo.setWipMin(config.getWipMin());
                vo.setWipMax(config.getWipMax());
                // 标记：如果隐藏且有工单使用该状态
                vo.setHasHiddenIssues(!config.getVisible() && usedStatusIds.contains(status.getId()));
            } else {
                // 新增的全局状态（没有配置记录）：默认不可见
                boolean hasIssues = usedStatusIds.contains(status.getId());
                vo.setVisible(false);
                vo.setSortOrder(status.getSortOrder() + 1000);
                vo.setCollapsed(false);
                vo.setHasHiddenIssues(hasIssues);
            }

            // 新增字段
            vo.setIssueCount(issueCountMap.getOrDefault(status.getId(), 0));
            vo.setInWorkflow(workflowStatusIds.contains(status.getId()));
            result.add(vo);
        }

        // 按 sortOrder 排序
        result.sort(Comparator.comparingInt(BoardColumnVO::getSortOrder));
        return result;
    }

    /**
     * 查询项目中已有工单使用的状态 ID 集合（高性能 DISTINCT 查询）。
     * 仅用于 initializeColumns（不需要缓存的初始化场景）。
     */
    private Set<Long> getProjectUsedStatusIds(Long projectId) {
        Set<Long> ids = issueMapper.selectDistinctStatusIdsByProject(projectId);
        return ids != null ? ids : Collections.emptySet();
    }

    /**
     * 查询项目中各状态的工单数量。
     * 仅用于 initializeColumns（不需要缓存的初始化场景）。
     */
    private Map<Long, Integer> getProjectIssueCountByStatus(Long projectId) {
        List<Map<String, Object>> rows = issueMapper.selectIssueCountByStatus(projectId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long statusId = ((Number) row.get("status_id")).longValue();
            Integer count = ((Number) row.get("cnt")).intValue();
            result.put(statusId, count);
        }
        return result;
    }

    /**
     * 查询项目工作流中涉及的所有状态 ID。
     * 仅用于 initializeColumns（不需要缓存的初始化场景）。
     */
    private Set<Long> getProjectWorkflowStatusIds(Long projectId) {
        Set<Long> ids = workflowTransitionMapper.selectWorkflowStatusIds(projectId);
        return ids != null ? ids : Collections.emptySet();
    }

    // ========== Cache methods ==========

    /**
     * 使指定项目的看板列缓存失效。
     * 在列配置保存、工单状态变更等场景调用。
     */
    public void invalidateCache(Long projectId) {
        try {
            String cacheKey = CACHE_PREFIX + projectId;
            redisTemplate.delete(cacheKey);
            log.debug("Board columns cache invalidated: projectId={}", projectId);
        } catch (Exception e) {
            log.debug("Board columns cache invalidation failed (projectId={}): {}", projectId, e.getMessage());
        }
    }

    private List<BoardColumnVO> getFromCache(Long projectId) {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_PREFIX + projectId);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<BoardColumnVO>>() {});
            }
        } catch (Exception e) {
            log.debug("Board columns cache read failed (projectId={}): {}", projectId, e.getMessage());
        }
        return null;
    }

    private void putToCache(Long projectId, List<BoardColumnVO> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(CACHE_PREFIX + projectId, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Board columns cache write failed (projectId={}): {}", projectId, e.getMessage());
        }
    }
}

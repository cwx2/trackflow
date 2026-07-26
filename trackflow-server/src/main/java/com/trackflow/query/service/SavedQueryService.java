package com.trackflow.query.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.project.service.ProjectService;
import com.trackflow.query.dto.CreateQueryDTO;
import com.trackflow.query.dto.ExecuteQueryDTO;
import com.trackflow.query.dto.UpdateQueryDTO;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.query.entity.SavedQuery;
import com.trackflow.query.entity.UserQueryFavorite;
import com.trackflow.query.mapper.SavedQueryMapper;
import com.trackflow.query.mapper.UserQueryFavoriteMapper;
import com.trackflow.query.vo.QueryPanelItemVO;
import com.trackflow.query.vo.QueryPanelVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedQueryService {

    private final SavedQueryMapper queryMapper;
    private final UserQueryFavoriteMapper favoriteMapper;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;

    /**
     * 获取用户的查询面板（左侧面板数据）
     * 只返回：用户自己创建的查询 + 用户收藏的共享查询
     * 如果用户没有任何收藏记录（首次使用），自动收藏默认查询
     */
    public QueryPanelVO getPanel(Long userId, Long projectId) {
        // 确保用户有收藏记录（首次使用时自动初始化）
        ensureDefaultFavorites(userId);

        // 获取用户收藏的查询 ID 集合
        LambdaQueryWrapper<UserQueryFavorite> favWrapper = new LambdaQueryWrapper<>();
        favWrapper.eq(UserQueryFavorite::getUserId, userId);
        favWrapper.orderByAsc(UserQueryFavorite::getSortOrder);
        List<UserQueryFavorite> favorites = favoriteMapper.selectList(favWrapper);
        Set<Long> favoriteQueryIds = favorites.stream()
                .map(UserQueryFavorite::getQueryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 查询用户可见的查询：自己创建的 + 已收藏的共享查询
        LambdaQueryWrapper<SavedQuery> wrapper = new LambdaQueryWrapper<>();
        if (favoriteQueryIds.isEmpty()) {
            // 只查自己创建的
            wrapper.eq(SavedQuery::getUserId, userId);
        } else {
            wrapper.and(w -> w
                    .eq(SavedQuery::getUserId, userId)
                    .or()
                    .in(SavedQuery::getId, favoriteQueryIds)
            );
        }
        if (projectId != null) {
            wrapper.and(w -> w
                    .eq(SavedQuery::getProjectId, projectId)
                    .or()
                    .isNull(SavedQuery::getProjectId)
            );
        }
        wrapper.orderByAsc(SavedQuery::getSortOrder);

        List<SavedQuery> queries = queryMapper.selectList(wrapper);

        // 计数范围：选中项目时仅统计该项目，否则统计所有可访问项目
        List<Long> countProjectIds = (projectId != null)
                ? Collections.singletonList(projectId)
                : projectService.getAccessibleProjectIds(userId);

        // 分为 pinned 和普通
        List<QueryPanelItemVO> pinned = new ArrayList<>();
        List<QueryPanelItemVO> normal = new ArrayList<>();

        for (SavedQuery q : queries) {
            // 只展示：用户自己的查询 OR 已收藏的查询
            boolean isOwn = userId.equals(q.getUserId());
            boolean isFavorited = favoriteQueryIds.contains(q.getId());
            if (!isOwn && !isFavorited) continue;

            long count = countForQueryWithProjectFilter(q, countProjectIds);
            QueryPanelItemVO item = QueryPanelItemVO.builder()
                    .id(String.valueOf(q.getId()))
                    .name(q.getName())
                    .folder(q.getFolder())
                    .icon(q.getIcon())
                    .pinned(q.getPinned())
                    .shared(q.getShared())
                    .userId(q.getUserId() != null ? String.valueOf(q.getUserId()) : null)
                    .count(count)
                    .filters(q.getFilters())
                    .favorited(isFavorited || isOwn)
                    .build();

            if (Boolean.TRUE.equals(q.getPinned())) {
                pinned.add(item);
            } else {
                normal.add(item);
            }
        }

        return new QueryPanelVO(pinned, normal);
    }

    /**
     * 获取所有可用的共享查询（供"管理查询"弹窗使用）
     * 返回所有 shared=true 的查询，并标记当前用户是否已收藏
     */
    public List<QueryPanelItemVO> getAvailableQueries(Long userId, Long projectId) {
        LambdaQueryWrapper<SavedQuery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SavedQuery::getShared, true);
        if (projectId != null) {
            wrapper.and(w -> w
                    .eq(SavedQuery::getProjectId, projectId)
                    .or()
                    .isNull(SavedQuery::getProjectId)
            );
        }
        wrapper.orderByAsc(SavedQuery::getSortOrder);
        List<SavedQuery> queries = queryMapper.selectList(wrapper);

        // 获取用户已收藏的集合
        LambdaQueryWrapper<UserQueryFavorite> favWrapper = new LambdaQueryWrapper<>();
        favWrapper.eq(UserQueryFavorite::getUserId, userId);
        Set<Long> favoriteIds = favoriteMapper.selectList(favWrapper).stream()
                .map(UserQueryFavorite::getQueryId)
                .collect(Collectors.toSet());

        return queries.stream().map(q -> QueryPanelItemVO.builder()
                .id(String.valueOf(q.getId()))
                .name(q.getName())
                .folder(q.getFolder())
                .icon(q.getIcon())
                .pinned(q.getPinned())
                .shared(q.getShared())
                .userId(q.getUserId() != null ? String.valueOf(q.getUserId()) : null)
                .count(0) // 不计数（性能考虑）
                .filters(q.getFilters())
                .favorited(favoriteIds.contains(q.getId()))
                .build()
        ).toList();
    }

    /**
     * 收藏查询（添加到面板）
     */
    @Transactional
    public void addFavorite(Long userId, Long queryId) {
        // 验证查询存在且是共享的
        SavedQuery query = queryMapper.selectById(queryId);
        if (query == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "查询不存在");
        }
        if (!Boolean.TRUE.equals(query.getShared()) && !query.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无法收藏非共享查询");
        }

        // 检查是否已收藏
        LambdaQueryWrapper<UserQueryFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQueryFavorite::getUserId, userId)
                .eq(UserQueryFavorite::getQueryId, queryId);
        if (favoriteMapper.selectCount(wrapper) > 0) {
            return; // 已收藏，幂等返回
        }

        // 获取当前最大 sortOrder
        LambdaQueryWrapper<UserQueryFavorite> maxWrapper = new LambdaQueryWrapper<>();
        maxWrapper.eq(UserQueryFavorite::getUserId, userId)
                .orderByDesc(UserQueryFavorite::getSortOrder)
                .last("LIMIT 1");
        UserQueryFavorite maxFav = favoriteMapper.selectOne(maxWrapper);
        int nextOrder = (maxFav != null) ? maxFav.getSortOrder() + 1 : 0;

        UserQueryFavorite favorite = new UserQueryFavorite();
        favorite.setUserId(userId);
        favorite.setQueryId(queryId);
        favorite.setSortOrder(nextOrder);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);
    }

    /**
     * 取消收藏查询（从面板移除）
     */
    @Transactional
    public void removeFavorite(Long userId, Long queryId) {
        LambdaQueryWrapper<UserQueryFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQueryFavorite::getUserId, userId)
                .eq(UserQueryFavorite::getQueryId, queryId);
        favoriteMapper.delete(wrapper);
    }

    /**
     * 系统默认收藏的查询名称列表（YouTrack 标准预设）
     * 新增预设查询后，在此列表追加名称即可自动同步给所有用户
     */
    private static final List<String> DEFAULT_QUERY_NAMES = List.of("分配给我", "我报告的", "我评论的");

    /**
     * 确保用户拥有全部默认收藏（差量补充模式）。
     * <p>
     * 与旧的"首次初始化"不同，此方法每次调用都会检查并补充缺失的默认查询，
     * 保证新增的预设查询能自动出现在所有用户的侧边栏中。
     */
    private void ensureDefaultFavorites(Long userId) {
        // 查找所有系统默认查询（按名称匹配 shared 查询）
        LambdaQueryWrapper<SavedQuery> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SavedQuery::getName, DEFAULT_QUERY_NAMES)
                .eq(SavedQuery::getShared, true)
                .orderByAsc(SavedQuery::getSortOrder);
        List<SavedQuery> defaultQueries = queryMapper.selectList(queryWrapper);
        if (defaultQueries.isEmpty()) return;

        // 获取用户已收藏的查询 ID 集合
        LambdaQueryWrapper<UserQueryFavorite> favWrapper = new LambdaQueryWrapper<>();
        favWrapper.eq(UserQueryFavorite::getUserId, userId);
        Set<Long> existingFavIds = favoriteMapper.selectList(favWrapper).stream()
                .map(UserQueryFavorite::getQueryId)
                .collect(Collectors.toSet());

        // 获取当前最大 sortOrder（用于追加新收藏时排在已有项之后）
        LambdaQueryWrapper<UserQueryFavorite> maxWrapper = new LambdaQueryWrapper<>();
        maxWrapper.eq(UserQueryFavorite::getUserId, userId)
                .orderByDesc(UserQueryFavorite::getSortOrder)
                .last("LIMIT 1");
        UserQueryFavorite maxFav = favoriteMapper.selectOne(maxWrapper);
        int nextOrder = (maxFav != null) ? maxFav.getSortOrder() + 1 : 0;

        // 差量补充：只为尚未收藏的默认查询创建收藏记录
        for (SavedQuery q : defaultQueries) {
            if (!existingFavIds.contains(q.getId())) {
                UserQueryFavorite fav = new UserQueryFavorite();
                fav.setUserId(userId);
                fav.setQueryId(q.getId());
                fav.setSortOrder(nextOrder++);
                fav.setCreatedAt(LocalDateTime.now());
                favoriteMapper.insert(fav);
                log.debug("为用户 {} 补充默认收藏查询: {}", userId, q.getName());
            }
        }
    }

    /**
     * 创建保存查询
     */
    @Transactional
    public SavedQuery create(Long userId, CreateQueryDTO dto) {
        SavedQuery query = new SavedQuery();
        query.setName(dto.getName());
        query.setProjectId(dto.getProjectId());
        query.setUserId(userId);
        query.setShared(dto.getShared() != null ? dto.getShared() : false);
        query.setPinned(dto.getPinned() != null ? dto.getPinned() : false);
        query.setFolder(dto.getFolder());
        query.setFilters(toJson(dto.getFilters()));
        query.setColumns(toJson(dto.getColumns()));
        query.setSortCriteria(toJson(dto.getSortCriteria()));
        query.setGroupBy(dto.getGroupBy());
        query.setIcon(dto.getIcon());
        query.setSortOrder(0);
        query.setCreatedAt(LocalDateTime.now());
        query.setUpdatedAt(LocalDateTime.now());
        queryMapper.insert(query);
        return query;
    }

    /**
     * 更新保存查询
     */
    @Transactional
    public SavedQuery update(Long id, Long userId, UpdateQueryDTO dto) {
        SavedQuery query = getByIdAndUser(id, userId);

        if (dto.getName() != null) query.setName(dto.getName());
        if (dto.getShared() != null) query.setShared(dto.getShared());
        if (dto.getPinned() != null) query.setPinned(dto.getPinned());
        if (dto.getFolder() != null) query.setFolder(dto.getFolder());
        if (dto.getFilters() != null) query.setFilters(toJson(dto.getFilters()));
        if (dto.getColumns() != null) query.setColumns(toJson(dto.getColumns()));
        if (dto.getSortCriteria() != null) query.setSortCriteria(toJson(dto.getSortCriteria()));
        if (dto.getGroupBy() != null) query.setGroupBy(dto.getGroupBy());
        if (dto.getSortOrder() != null) query.setSortOrder(dto.getSortOrder());
        // icon 允许设为 null（清除图标），使用特殊标记区分"未传"和"传了 null"
        // 由于 JSON 反序列化时 missing key 和 explicit null 都是 null，
        // 这里直接 setIcon（如果前端传了 icon 字段就更新，不传则不更新）
        if (dto.getIcon() != null) query.setIcon(dto.getIcon());

        query.setUpdatedAt(LocalDateTime.now());
        queryMapper.updateById(query);
        return query;
    }

    /**
     * 删除保存查询
     */
    @Transactional
    public void delete(Long id, Long userId) {
        SavedQuery query = getByIdAndUser(id, userId);
        queryMapper.deleteById(query.getId());
    }

    /**
     * 执行保存查询（返回匹配的 Issue 列表）
     */
    public Page<Issue> executeById(Long id, int page, int pageSize) {
        SavedQuery query = queryMapper.selectById(id);
        if (query == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Query not found");
        }

        List<Map<String, Object>> filters = parseFilters(query.getFilters());
        List<Map<String, String>> sortCriteria = parseSortCriteria(query.getSortCriteria());
        return queryExecutor.execute(filters, page, pageSize, sortCriteria);
    }

    /**
     * 即时执行查询（不保存）
     */
    public Page<Issue> executeAdhoc(ExecuteQueryDTO dto) {
        int page = dto.getPage() != null ? dto.getPage() : 1;
        int pageSize = dto.getPageSize() != null ? dto.getPageSize() : 20;
        return queryExecutor.execute(dto.getFilters(), page, pageSize, dto.getSortCriteria());
    }

    /**
     * 执行保存查询（带项目成员过滤）
     * 查询结果自动限定在用户所属项目范围内
     */
    public Page<Issue> executeByIdWithAccessCheck(Long id, int page, int pageSize, Long userId, boolean hideResolved) {
        SavedQuery query = queryMapper.selectById(id);
        if (query == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Query not found");
        }

        List<Map<String, Object>> filters = parseFilters(query.getFilters());
        List<Map<String, String>> sortCriteria = parseSortCriteria(query.getSortCriteria());

        // 注入项目成员过滤条件
        List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(userId);
        return queryExecutor.executeWithProjectFilter(filters, page, pageSize, sortCriteria, accessibleProjectIds, hideResolved);
    }

    /**
     * 即时执行查询（带项目成员过滤）
     */
    public Page<Issue> executeAdhocWithAccessCheck(ExecuteQueryDTO dto, Long userId) {
        int page = dto.getPage() != null ? dto.getPage() : 1;
        int pageSize = dto.getPageSize() != null ? dto.getPageSize() : 20;

        List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(userId);
        return queryExecutor.executeWithProjectFilter(dto.getFilters(), page, pageSize, dto.getSortCriteria(), accessibleProjectIds);
    }

    /**
     * 批量获取查询计数（带项目成员过滤）
     */
    public Map<String, Long> batchCountWithAccessCheck(List<Long> queryIds, Long userId, Long projectId) {
        // 计数范围：选中项目时仅统计该项目，否则统计所有可访问项目
        List<Long> countProjectIds = (projectId != null)
                ? Collections.singletonList(projectId)
                : projectService.getAccessibleProjectIds(userId);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Long queryId : queryIds) {
            SavedQuery query = queryMapper.selectById(queryId);
            if (query != null) {
                result.put(String.valueOf(queryId), countForQueryWithProjectFilter(query, countProjectIds));
            }
        }
        return result;
    }

    /**
     * 批量获取查询计数
     */
    public Map<String, Long> batchCount(List<Long> queryIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Long queryId : queryIds) {
            SavedQuery query = queryMapper.selectById(queryId);
            if (query != null) {
                result.put(String.valueOf(queryId), countForQuery(query));
            }
        }
        return result;
    }

    /**
     * 排序查询面板
     */
    @Transactional
    public void reorder(List<Map<String, Object>> orders) {
        for (Map<String, Object> order : orders) {
            Long id = Long.valueOf(order.get("id").toString());
            Integer sortOrder = (Integer) order.get("sortOrder");
            Boolean pinned = (Boolean) order.get("pinned");

            SavedQuery query = queryMapper.selectById(id);
            if (query != null) {
                if (sortOrder != null) query.setSortOrder(sortOrder);
                if (pinned != null) query.setPinned(pinned);
                queryMapper.updateById(query);
            }
        }
    }

    // ========== 内部方法 ==========

    private SavedQuery getByIdAndUser(Long id, Long userId) {
        SavedQuery query = queryMapper.selectById(id);
        if (query == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Query not found");
        }
        // 只有创建者可以修改/删除
        if (!query.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You can only modify your own queries");
        }
        return query;
    }

    private long countForQuery(SavedQuery query) {
        try {
            List<Map<String, Object>> filters = parseFilters(query.getFilters());
            return queryExecutor.count(filters);
        } catch (Exception e) {
            log.warn("Failed to count for query {}: {}", query.getId(), e.getMessage());
            return 0;
        }
    }

    private long countForQueryWithProjectFilter(SavedQuery query, List<Long> accessibleProjectIds) {
        try {
            List<Map<String, Object>> filters = parseFilters(query.getFilters());
            return queryExecutor.countWithProjectFilter(filters, accessibleProjectIds);
        } catch (Exception e) {
            log.warn("Failed to count for query {}: {}", query.getId(), e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFilters(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseSortCriteria(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}

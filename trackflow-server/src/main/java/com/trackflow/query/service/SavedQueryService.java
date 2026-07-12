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
import com.trackflow.query.mapper.SavedQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedQueryService {

    private final SavedQueryMapper queryMapper;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;

    /**
     * 获取用户的查询面板（左侧面板数据）
     * 返回 pinned + 普通查询，包含实时计数
     */
    public Map<String, Object> getPanel(Long userId, Long projectId) {
        // 查询用户可见的查询（自己的 + 共享的）
        LambdaQueryWrapper<SavedQuery> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .eq(SavedQuery::getUserId, userId)
                .or()
                .eq(SavedQuery::getShared, true)
        );
        if (projectId != null) {
            wrapper.and(w -> w
                    .eq(SavedQuery::getProjectId, projectId)
                    .or()
                    .isNull(SavedQuery::getProjectId)
            );
        }
        wrapper.orderByAsc(SavedQuery::getSortOrder);

        List<SavedQuery> queries = queryMapper.selectList(wrapper);

        // 获取用户可访问项目列表（用于计数过滤）
        List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(userId);

        // 分为 pinned 和普通
        List<Map<String, Object>> pinned = new ArrayList<>();
        List<Map<String, Object>> normal = new ArrayList<>();

        for (SavedQuery q : queries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", q.getId());
            item.put("name", q.getName());
            item.put("folder", q.getFolder());
            item.put("pinned", q.getPinned());
            item.put("shared", q.getShared());

            // 实时计数（带项目成员过滤）
            long count = countForQueryWithProjectFilter(q, accessibleProjectIds);
            item.put("count", count);

            if (Boolean.TRUE.equals(q.getPinned())) {
                pinned.add(item);
            } else {
                normal.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pinned", pinned);
        result.put("queries", normal);
        return result;
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
    public Page<Issue> executeByIdWithAccessCheck(Long id, int page, int pageSize, Long userId) {
        SavedQuery query = queryMapper.selectById(id);
        if (query == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Query not found");
        }

        List<Map<String, Object>> filters = parseFilters(query.getFilters());
        List<Map<String, String>> sortCriteria = parseSortCriteria(query.getSortCriteria());

        // 注入项目成员过滤条件
        List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(userId);
        return queryExecutor.executeWithProjectFilter(filters, page, pageSize, sortCriteria, accessibleProjectIds);
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
    public Map<Long, Long> batchCountWithAccessCheck(List<Long> queryIds, Long userId) {
        List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(userId);
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long queryId : queryIds) {
            SavedQuery query = queryMapper.selectById(queryId);
            if (query != null) {
                result.put(queryId, countForQueryWithProjectFilter(query, accessibleProjectIds));
            }
        }
        return result;
    }

    /**
     * 批量获取查询计数
     */
    public Map<Long, Long> batchCount(List<Long> queryIds) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long queryId : queryIds) {
            SavedQuery query = queryMapper.selectById(queryId);
            if (query != null) {
                result.put(queryId, countForQuery(query));
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

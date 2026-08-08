package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.constant.IssueStatusCategory;
import com.trackflow.common.util.SqlUtils;
import com.trackflow.customfield.service.CustomFieldSortHelper;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.util.IssuePriorityHelper;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单查询服务 — 处理 IssueQuery 条件构建、关键词搜索、自定义字段排序等列表查询逻辑。
 * <p>
 * 从 IssueService 拆分，将复杂的 QueryWrapper 构建和过滤逻辑集中管理。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueQueryService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final ProjectService projectService;
    private final StatusCacheHelper statusCacheHelper;
    private final CustomFieldSortHelper customFieldSortHelper;

    /**
     * Issue 列表（接受 IssueQuery，完整筛选支持）。
     * 强制按用户所属项目过滤。
     */
    @Transactional(readOnly = true)
    public Page<Issue> listByQuery(IssueQuery query) {
        Long currentUserId = com.trackflow.common.util.SecurityUtils.getCurrentUserId();
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (query.getProjectId() != null) {
            projectService.assertProjectAccessible(currentUserId, query.getProjectId());
            wrapper.eq("project_id", query.getProjectId());
        } else {
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                if (accessibleProjectIds.isEmpty()) {
                    return new Page<>();
                }
                wrapper.in("project_id", accessibleProjectIds);
            }
        }

        // Standard filters
        applyFilter(wrapper, "status_id", query.getStatusId(), true);
        applyFilter(wrapper, "priority", query.getPriority(), false);
        if (!"true".equalsIgnoreCase(query.getAssignedToMe())) {
            applyFilter(wrapper, "assignee_id", query.getAssigneeId(), true);
        }
        if (query.getAssigneeName() != null && !query.getAssigneeName().isBlank()) {
            wrapper.apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name = {0})", query.getAssigneeName().trim());
        }
        if (query.getReporterId() != null) wrapper.eq("reporter_id", query.getReporterId());
        applyFilter(wrapper, "sprint_id", query.getSprintId(), true);
        applyFilter(wrapper, "issue_type", query.getIssueType(), false);

        // Negative filters
        applyNegativeFilter(wrapper, "status_id", query.getStatusIdNot(), true);
        applyNegativeFilter(wrapper, "priority", query.getPriorityNot(), false);
        applyNegativeFilter(wrapper, "assignee_id", query.getAssigneeIdNot(), true);
        applyNegativeFilter(wrapper, "sprint_id", query.getSprintIdNot(), true);
        applyNegativeFilter(wrapper, "issue_type", query.getIssueTypeNot(), false);

        // Tag filter
        if (query.getTagId() != null && !query.getTagId().isBlank()) {
            String tagIdValue = query.getTagId().trim();
            if (tagIdValue.contains(",")) {
                List<Long> tagIds = java.util.Arrays.stream(tagIdValue.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(Long::parseLong).toList();
                wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id IN ("
                        + tagIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "))");
            } else {
                wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id = {0})",
                        Long.parseLong(tagIdValue));
            }
        }

        // Parent/child relationship filters
        if (query.getParentId() != null) {
            wrapper.eq("parent_id", query.getParentId());
        }
        if ("true".equals(query.getHasParent())) {
            wrapper.isNotNull("parent_id");
        } else if ("false".equals(query.getHasParent())) {
            wrapper.isNull("parent_id");
        }

        // Date range filters
        if (query.getCreatedAfter() != null) {
            wrapper.ge("created_at", query.getCreatedAfter().atStartOfDay());
        }
        if (query.getCreatedBefore() != null) {
            wrapper.le("created_at", query.getCreatedBefore().atTime(23, 59, 59));
        }
        if (query.getUpdatedAfter() != null) {
            wrapper.ge("updated_at", query.getUpdatedAfter().atStartOfDay());
        }
        if (query.getUpdatedBefore() != null) {
            wrapper.le("updated_at", query.getUpdatedBefore().atTime(23, 59, 59));
        }
        if (query.getResolvedAfter() != null) {
            wrapper.ge("resolved_at", query.getResolvedAfter().atStartOfDay());
        }
        if (query.getResolvedBefore() != null) {
            wrapper.le("resolved_at", query.getResolvedBefore().atTime(23, 59, 59));
        }

        // hideResolved / onlyResolved
        if ("true".equals(query.getHideResolved())) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.notIn("status_id", closedStatusIds);
            }
        }
        if ("true".equals(query.getOnlyResolved())) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.in("status_id", closedStatusIds);
            } else {
                wrapper.apply("1 = 0");
            }
        }

        // Special filters
        boolean needClosedExclusion = "true".equals(query.getOverdue())
                || "true".equals(query.getDueSoon())
                || "true".equals(query.getReportedByMe());
        if (needClosedExclusion) {
            List<IssueStatus> allStatuses = statusMapper.selectList(null);
            List<Long> closedIds = allStatuses.stream()
                    .filter(s -> IssueStatusCategory.isClosed(s.getCategory()))
                    .map(IssueStatus::getId).toList();
            if (!closedIds.isEmpty()) {
                wrapper.notIn("status_id", closedIds);
            }
        }
        if ("true".equals(query.getOverdue()) || "true".equals(query.getDueSoon())) {
            wrapper.isNotNull("due_date");
            if ("true".equals(query.getOverdue())) {
                wrapper.lt("due_date", LocalDate.now());
            }
            if ("true".equals(query.getDueSoon())) {
                wrapper.le("due_date", LocalDate.now().plusDays(7));
            }
        }
        if (query.getDueAfter() != null) {
            wrapper.ge("due_date", query.getDueAfter());
        }
        if (query.getDueBefore() != null) {
            wrapper.le("due_date", query.getDueBefore());
        }
        if ("true".equals(query.getReportedByMe())) {
            wrapper.eq("reporter_id", currentUserId);
        }
        if ("true".equalsIgnoreCase(query.getAssignedToMe())) {
            wrapper.eq("assignee_id", currentUserId);
        }

        // excludeDoneBefore
        if (query.getExcludeDoneBefore() != null) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.and(w -> w
                        .notIn("status_id", closedStatusIds)
                        .or()
                        .ge("resolved_at", query.getExcludeDoneBefore().atStartOfDay())
                        .or()
                        .isNull("resolved_at")
                );
            }
        }

        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            applyKeywordFilter(wrapper, keyword);
        }

        // Sorting
        String sort = query.getSort();
        boolean hasCustomFieldSort = false;
        boolean hasSpecialSort = false;
        if (sort != null && !sort.isBlank()) {
            boolean desc = sort.startsWith("-");
            String sortField = desc ? sort.substring(1) : sort;
            if (customFieldSortHelper.isCustomFieldSortKey(sortField)) {
                hasCustomFieldSort = customFieldSortHelper.applyCustomFieldSort(wrapper, sortField, !desc);
            } else if ("remaining".equals(sortField)) {
                hasSpecialSort = true;
                if (desc) {
                    wrapper.last("ORDER BY (COALESCE(estimated_hours, 0) - COALESCE(spent_hours, 0)) DESC NULLS LAST");
                } else {
                    wrapper.last("ORDER BY (COALESCE(estimated_hours, 0) - COALESCE(spent_hours, 0)) ASC NULLS LAST");
                }
            } else if ("priority".equals(sortField)) {
                hasSpecialSort = true;
                if (desc) {
                    wrapper.last("ORDER BY " + IssuePriorityHelper.PRIORITY_ORDER_EXPR + " DESC, updated_at DESC");
                } else {
                    wrapper.last("ORDER BY " + IssuePriorityHelper.PRIORITY_ORDER_EXPR + " ASC, updated_at DESC");
                }
            }
        }

        if (!hasCustomFieldSort && !hasSpecialSort) {
            wrapper.orderByDesc("updated_at");
        }

        if (hasCustomFieldSort || hasSpecialSort) {
            String originalSort = query.getSort();
            query.setSort(null);
            Page<Issue> result = issueMapper.selectPage(query.toPage(), wrapper);
            query.setSort(originalSort);
            return result;
        }
        return issueMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 查找与给定关键词相似的工单（重复检测）。
     */
    public List<Issue> findSimilarIssues(String keyword, Long projectId, int limit) {
        if (keyword == null || keyword.isBlank() || keyword.length() < 3) {
            return Collections.emptyList();
        }

        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (projectId != null) {
            wrapper.eq("project_id", projectId);
        } else {
            Long currentUserId = com.trackflow.common.util.SecurityUtils.getCurrentUserId();
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                if (accessibleProjectIds.isEmpty()) {
                    return Collections.emptyList();
                }
                wrapper.in("project_id", accessibleProjectIds);
            }
        }

        applyKeywordFilter(wrapper, keyword);
        wrapper.orderByDesc("updated_at");

        Page<Issue> page = new Page<>(1, limit);
        page.setSearchCount(false);
        Page<Issue> result = issueMapper.selectPage(page, wrapper);

        return result.getRecords();
    }

    // ========== 过滤辅助方法 ==========

    private void applyFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        if ("none".equalsIgnoreCase(value.trim())) {
            wrapper.isNull(column);
            return;
        }
        if (value.contains(",")) {
            List<?> values = isNumeric
                    ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                    : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            wrapper.in(column, values);
        } else {
            if (isNumeric) {
                wrapper.eq(column, Long.parseLong(value.trim()));
            } else {
                wrapper.eq(column, value.trim());
            }
        }
    }

    private void applyNegativeFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        if ("none".equalsIgnoreCase(value.trim())) {
            wrapper.isNotNull(column);
            return;
        }
        List<?> values = isNumeric
                ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        wrapper.notIn(column, values);
    }

    private void applyKeywordFilter(QueryWrapper<Issue> wrapper, String keyword) {
        String escaped = SqlUtils.escapeLikePattern(keyword);
        String likePattern = "%" + escaped + "%";
        wrapper.and(w -> w
                .apply("to_tsvector('simple', COALESCE(title,'') || ' ' || COALESCE(description,'')) @@ plainto_tsquery('simple', {0})", keyword)
                .or()
                .apply("title ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                .apply("description ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                .apply("issue_key ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                .apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name ILIKE {0} ESCAPE '\\' OR username ILIKE {0} ESCAPE '\\')", likePattern)
        );
    }
}

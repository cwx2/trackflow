package com.trackflow.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.SqlUtils;
import com.trackflow.customfield.service.CustomFieldSortHelper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.service.StatusCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 查询执行引擎：将 JSON 筛选条件动态转换为 SQL 查询
 *
 * 筛选条件格式:
 * [
 *   {"field": "status", "operator": "in", "value": ["Open", "In Progress"]},
 *   {"field": "assignee", "operator": "eq", "value": ["${currentUser}"]},
 *   {"field": "priority", "operator": "eq", "value": ["High"]},
 *   {"field": "keyword", "operator": "contains", "value": ["登录"]}
 * ]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExecutor {

    private final IssueMapper issueMapper;
    private final StatusCacheHelper statusCacheHelper;
    private final CustomFieldSortHelper customFieldSortHelper;

    /**
     * 允许排序的字段白名单（数据库列名）
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "issue_key", "title", "status_id", "priority",
            "assignee_id", "reporter_id", "created_at", "updated_at",
            "due_date", "sprint_id", "issue_type", "project_id"
    );

    /**
     * 自定义字段 Key 合法格式：字母或数字开头，允许字母、数字、下划线，最长 64 字符
     * 实际存储的 key 可能是纯数字 ID（如 snowflake ID）或字母命名的字段
     */
    private static final Pattern CF_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_]{0,63}$");

    /**
     * 状态 code 合法格式：小写字母、数字、下划线，最长 64 字符
     */
    private static final Pattern STATUS_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /**
     * 允许的筛选操作符白名单
     */
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "eq", "neq", "in", "not_in", "is_empty", "is_not_empty",
            "contains", "gt", "gte", "lt", "lte", "between",
            "open", "closed"
    );

    /**
     * 执行筛选查询
     */
    public Page<Issue> execute(List<Map<String, Object>> filters, int page, int pageSize, List<Map<String, String>> sortCriteria) {
        Page<Issue> pageObj = new Page<>(page, pageSize);
        QueryWrapper<Issue> wrapper = buildWrapper(filters);

        // 排序
        applySortCriteria(wrapper, sortCriteria);

        return issueMapper.selectPage(pageObj, wrapper);
    }

    /**
     * 仅返回匹配筛选条件的工单 ID 列表（用于报表 Issue filter 功能）
     * 带项目权限过滤，最大返回 10000 条（超出截断——报表场景已足够）
     */
    public List<Long> executeFilterToIds(List<Map<String, Object>> filters, List<Long> accessibleProjectIds) {
        if (filters == null || filters.isEmpty()) {
            return null; // null 表示无筛选，不限制
        }
        QueryWrapper<Issue> wrapper = buildWrapper(filters);
        wrapper.select("id");
        if (accessibleProjectIds != null) {
            if (accessibleProjectIds.isEmpty()) {
                return List.of(-1L);
            }
            wrapper.in("project_id", accessibleProjectIds);
        }
        wrapper.last("LIMIT 10000");
        return issueMapper.selectObjs(wrapper).stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();
    }

    /**
     * 计数查询（不分页，只返回匹配数量）
     */
    public long count(List<Map<String, Object>> filters) {
        QueryWrapper<Issue> wrapper = buildWrapper(filters);
        return issueMapper.selectCount(wrapper);
    }

    /**
     * 判断指定工单是否匹配给定的筛选条件。
     * 用于通知订阅匹配——评估 Saved Search 的 filters 是否覆盖该工单。
     *
     * @param issueId 工单 ID
     * @param filters 筛选条件 JSON 列表
     * @return true 如果工单匹配筛选条件
     */
    public boolean matchesIssue(Long issueId, List<Map<String, Object>> filters) {
        if (issueId == null) {
            return false;
        }
        QueryWrapper<Issue> wrapper = buildWrapper(filters);
        wrapper.eq("id", issueId);
        return issueMapper.selectCount(wrapper) > 0;
    }

    /**
     * 计数查询（带项目成员过滤）
     */
    public long countWithProjectFilter(List<Map<String, Object>> filters, List<Long> accessibleProjectIds) {
        QueryWrapper<Issue> wrapper = buildWrapper(filters);
        if (accessibleProjectIds != null) {
            if (accessibleProjectIds.isEmpty()) {
                return 0;
            }
            wrapper.in("project_id", accessibleProjectIds);
        }
        return issueMapper.selectCount(wrapper);
    }

    /**
     * 执行筛选查询（带项目成员过滤）
     * accessibleProjectIds 为 null 表示系统管理员，不限制；为空列表表示无可访问项目。
     */
    public Page<Issue> executeWithProjectFilter(List<Map<String, Object>> filters, int page, int pageSize,
                                                 List<Map<String, String>> sortCriteria, List<Long> accessibleProjectIds) {
        return executeWithProjectFilter(filters, page, pageSize, sortCriteria, accessibleProjectIds, false);
    }

    /**
     * 执行筛选查询（带项目成员过滤 + 隐藏已解决）
     */
    public Page<Issue> executeWithProjectFilter(List<Map<String, Object>> filters, int page, int pageSize,
                                                 List<Map<String, String>> sortCriteria, List<Long> accessibleProjectIds,
                                                 boolean hideResolved) {
        Page<Issue> pageObj = new Page<>(page, pageSize);
        QueryWrapper<Issue> wrapper = buildWrapper(filters);

        // 注入项目成员过滤
        if (accessibleProjectIds != null) {
            if (accessibleProjectIds.isEmpty()) {
                return new Page<>(); // 没有可访问的项目，返回空
            }
            wrapper.in("project_id", accessibleProjectIds);
        }

        // 隐藏已解决工单
        if (hideResolved) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.notIn("status_id", closedStatusIds);
            }
        }

        // 排序
        applySortCriteria(wrapper, sortCriteria);

        return issueMapper.selectPage(pageObj, wrapper);
    }

    /**
     * 应用排序条件（白名单校验 + 自定义字段排序支持）
     * <p>
     * 内置字段通过静态白名单校验；自定义字段（cf_ 前缀）委托给 CustomFieldSortHelper 处理。
     */
    private void applySortCriteria(QueryWrapper<Issue> wrapper, List<Map<String, String>> sortCriteria) {
        if (sortCriteria != null && !sortCriteria.isEmpty()) {
            boolean hasValidSort = false;
            for (Map<String, String> sort : sortCriteria) {
                String field = sort.get("field");
                if (field == null || field.isBlank()) continue;

                String direction = sort.getOrDefault("direction", "desc");
                boolean asc = "asc".equalsIgnoreCase(direction);

                // 检查是否是自定义字段排序
                if (customFieldSortHelper.isCustomFieldSortKey(field)) {
                    if (customFieldSortHelper.applyCustomFieldSort(wrapper, field, asc)) {
                        hasValidSort = true;
                    }
                } else {
                    // 内置字段排序（camelCase → snake_case 转换 + 白名单校验）
                    String columnName = camelToSnake(field);
                    if (!ALLOWED_SORT_FIELDS.contains(columnName)) {
                        log.warn("非法排序字段被拦截: {}", field);
                        continue;
                    }
                    if (asc) {
                        wrapper.orderByAsc(columnName);
                    } else {
                        wrapper.orderByDesc(columnName);
                    }
                    hasValidSort = true;
                }
            }
            if (!hasValidSort) {
                wrapper.orderByDesc("updated_at");
            }
        } else {
            wrapper.orderByDesc("updated_at");
        }
    }

    /**
     * 构建 QueryWrapper
     */
    @SuppressWarnings("unchecked")
    private QueryWrapper<Issue> buildWrapper(List<Map<String, Object>> filters) {
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (filters == null || filters.isEmpty()) return wrapper;

        Long currentUserId = SecurityUtils.getCurrentUserId();

        for (Map<String, Object> filter : filters) {
            String field = (String) filter.get("field");
            String operator = (String) filter.get("operator");
            Object value = filter.get("value");

            if (field == null || operator == null) continue;

            // 校验操作符合法性
            if (!ALLOWED_OPERATORS.contains(operator)) {
                log.warn("非法筛选操作符被拦截: {}", operator);
                continue;
            }

            // 解析动态变量
            List<String> values = resolveValues(value, currentUserId);

            switch (field) {
                case "project" -> applyUserFilter(wrapper, "project_id", operator, values, currentUserId);
                case "status" -> applyStatusFilter(wrapper, operator, values);
                case "priority" -> applyFilter(wrapper, "priority", operator, values);
                case "assignee" -> applyUserFilter(wrapper, "assignee_id", operator, values, currentUserId);
                case "reporter" -> applyUserFilter(wrapper, "reporter_id", operator, values, currentUserId);
                case "type" -> applyFilter(wrapper, "issue_type", operator, values);
                case "sprint" -> applyUserFilter(wrapper, "sprint_id", operator, values, currentUserId);
                case "keyword" -> applyKeywordFilter(wrapper, values);
                case "tag" -> applyTagFilter(wrapper, operator, values);
                case "parent" -> applyParentFilter(wrapper, operator, values);
                case "commenter" -> applyCommenterFilter(wrapper, operator, values, currentUserId);
                case "dueDate" -> applyDateFilter(wrapper, "due_date", operator, values);
                case "createdAt" -> applyDateFilter(wrapper, "created_at", operator, values);
                case "updatedAt" -> applyDateFilter(wrapper, "updated_at", operator, values);
                case "resolvedAt" -> applyDateFilter(wrapper, "resolved_at", operator, values);
                default -> {
                    // 自定义字段筛选（通过 custom_field_value EAV 表）
                    if (field.startsWith("cf.") || field.startsWith("customField.")) {
                        String cfKey = field.contains(".") ? field.substring(field.indexOf('.') + 1) : field;
                        applyCustomFieldFilter(wrapper, cfKey, operator, values);
                    } else {
                        log.warn("未知的筛选字段被忽略: {}", field);
                    }
                }
            }
        }

        return wrapper;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveValues(Object value, Long currentUserId) {
        if (value instanceof List) {
            return ((List<Object>) value).stream().map(v -> {
                String s = String.valueOf(v);
                if ("${currentUser}".equals(s) && currentUserId != null) {
                    return String.valueOf(currentUserId);
                }
                // ${today} kept as-is — resolved later in applyDateFilter
                return s;
            }).toList();
        }
        return List.of(String.valueOf(value));
    }

    private void applyFilter(QueryWrapper<Issue> wrapper, String column, String operator, List<String> values) {
        switch (operator) {
            case "eq" -> wrapper.eq(column, values.get(0));
            case "neq" -> wrapper.ne(column, values.get(0));
            case "in" -> wrapper.in(column, values);
            case "not_in" -> wrapper.notIn(column, values);
            case "is_empty" -> wrapper.isNull(column);
            case "is_not_empty" -> wrapper.isNotNull(column);
        }
    }

    private void applyStatusFilter(QueryWrapper<Issue> wrapper, String operator, List<String> values) {
        switch (operator) {
            case "eq", "in" -> {
                validateStatusCodes(values);
                wrapper.inSql("status_id",
                        "SELECT id FROM issue_status WHERE code IN (" + toSafeStatusCodeList(values) + ")");
            }
            case "not_in" -> {
                validateStatusCodes(values);
                wrapper.notInSql("status_id",
                        "SELECT id FROM issue_status WHERE code IN (" + toSafeStatusCodeList(values) + ")");
            }
            case "open" -> wrapper.inSql("status_id",
                    "SELECT id FROM issue_status WHERE is_closed = false");
            case "closed" -> wrapper.inSql("status_id",
                    "SELECT id FROM issue_status WHERE is_closed = true");
        }
    }

    private void applyUserFilter(QueryWrapper<Issue> wrapper, String column, String operator,
                                  List<String> values, Long currentUserId) {
        switch (operator) {
            case "eq" -> wrapper.eq(column, Long.parseLong(values.get(0)));
            case "neq" -> wrapper.ne(column, Long.parseLong(values.get(0)));
            case "in" -> wrapper.in(column, values.stream().map(Long::parseLong).toList());
            case "is_empty" -> wrapper.isNull(column);
            case "is_not_empty" -> wrapper.isNotNull(column);
        }
    }

    private void applyKeywordFilter(QueryWrapper<Issue> wrapper, List<String> values) {
        if (values.isEmpty()) return;
        String keyword = values.get(0);
        String escaped = SqlUtils.escapeLikePattern(keyword);
        String likePattern = "%" + escaped + "%";
        wrapper.and(w -> w
                // 全文搜索：利用 idx_issue_fulltext GIN 索引
                .apply("to_tsvector('simple', COALESCE(title,'') || ' ' || COALESCE(description,'')) @@ plainto_tsquery('simple', {0})", keyword)
                .or()
                // title 子串匹配：利用 idx_issue_title_trgm trigram GIN 索引
                // ESCAPE '\\' 确保 escapeLikePattern 转义的 \% 和 \_ 被 PostgreSQL 正确识别
                .apply("title ILIKE {0} ESCAPE '\\\\'", likePattern)
                .or()
                // description 子串匹配：利用 idx_issue_description_trgm trigram GIN 索引
                .apply("description ILIKE {0} ESCAPE '\\\\'", likePattern)
                .or()
                // issue_key 匹配
                .apply("issue_key ILIKE {0} ESCAPE '\\\\'", likePattern)
                .or()
                // assignee 名称匹配
                .apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name ILIKE {0} ESCAPE '\\\\' OR username ILIKE {0} ESCAPE '\\\\')", likePattern)
        );
    }

    private void applyTagFilter(QueryWrapper<Issue> wrapper, String operator, List<String> values) {
        if (values.isEmpty()) return;
        switch (operator) {
            case "eq", "in" -> {
                // EXISTS subquery: issue has any of the specified tags (OR semantics)
                List<Long> tagIds = values.stream().map(Long::parseLong).toList();
                if (tagIds.size() == 1) {
                    wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id = {0})", tagIds.get(0));
                } else {
                    wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id IN ("
                            + tagIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "))");
                }
            }
            case "not_in" -> {
                // NOT EXISTS: issue does not have any of the specified tags
                List<Long> tagIds = values.stream().map(Long::parseLong).toList();
                wrapper.apply("NOT EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id AND itr.tag_id IN ("
                        + tagIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "))");
            }
            case "is_empty" -> wrapper.apply("NOT EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id)");
            case "is_not_empty" -> wrapper.apply("EXISTS (SELECT 1 FROM issue_tag_relation itr WHERE itr.issue_id = issue.id)");
        }
    }

    private void applyParentFilter(QueryWrapper<Issue> wrapper, String operator, List<String> values) {
        switch (operator) {
            case "eq" -> wrapper.eq("parent_id", Long.parseLong(values.get(0)));
            case "in" -> wrapper.in("parent_id", values.stream().map(Long::parseLong).toList());
            case "is_empty" -> wrapper.isNull("parent_id");     // top-level issues only
            case "is_not_empty" -> wrapper.isNotNull("parent_id"); // sub-tasks only
        }
    }

    private void applyCommenterFilter(QueryWrapper<Issue> wrapper, String operator,
                                       List<String> values, Long currentUserId) {
        switch (operator) {
            case "eq" -> {
                long userId = Long.parseLong(values.get(0));
                wrapper.apply(
                        "EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.user_id = {0} AND ic.deleted_at IS NULL)",
                        userId);
            }
            case "in" -> {
                List<Long> userIds = values.stream().map(Long::parseLong).toList();
                if (userIds.size() == 1) {
                    wrapper.apply(
                            "EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.user_id = {0} AND ic.deleted_at IS NULL)",
                            userIds.get(0));
                } else {
                    wrapper.apply(
                            "EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.user_id IN ("
                                    + userIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ") AND ic.deleted_at IS NULL)");
                }
            }
            case "neq" -> {
                long userId = Long.parseLong(values.get(0));
                wrapper.apply(
                        "NOT EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.user_id = {0} AND ic.deleted_at IS NULL)",
                        userId);
            }
            case "is_empty" -> wrapper.apply(
                    "NOT EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.deleted_at IS NULL)");
            case "is_not_empty" -> wrapper.apply(
                    "EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.deleted_at IS NULL)");
        }
    }

    private void applyDateFilter(QueryWrapper<Issue> wrapper, String column, String operator, List<String> values) {
        // Resolve relative date keywords to actual date ranges
        List<String> resolvedValues = values.stream()
                .map(this::resolveRelativeDate)
                .toList();

        switch (operator) {
            case "eq" -> {
                // For relative keywords that represent ranges (e.g. "this week"), use between
                String[] range = resolveRelativeDateRange(values.get(0));
                if (range != null) {
                    wrapper.between(column, range[0], range[1]);
                } else {
                    wrapper.ge(column, resolvedValues.get(0) + " 00:00:00");
                    wrapper.lt(column, resolvedValues.get(0) + " 23:59:59");
                }
            }
            case "gt" -> wrapper.gt(column, resolvedValues.get(0) + " 23:59:59");
            case "gte" -> wrapper.ge(column, resolvedValues.get(0) + " 00:00:00");
            case "lt" -> wrapper.lt(column, resolvedValues.get(0) + " 00:00:00");
            case "lte" -> wrapper.le(column, resolvedValues.get(0) + " 23:59:59");
            case "between" -> {
                if (resolvedValues.size() >= 2) {
                    wrapper.ge(column, resolvedValues.get(0) + " 00:00:00");
                    wrapper.le(column, resolvedValues.get(1) + " 23:59:59");
                }
            }
        }
    }

    /**
     * 解析相对日期关键词为具体日期字符串 (yyyy-MM-dd)。
     * 如果不是关键词，原样返回。
     */
    private String resolveRelativeDate(String value) {
        if (value == null) return null;
        java.time.LocalDate today = java.time.LocalDate.now();
        return switch (value) {
            case "${today}", "today", "今天" -> today.toString();
            case "yesterday", "昨天" -> today.minusDays(1).toString();
            case "this week", "本周" -> today.with(java.time.DayOfWeek.MONDAY).toString();
            case "last week", "上周" -> today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY).toString();
            case "this month", "本月" -> today.withDayOfMonth(1).toString();
            case "last month", "上月" -> today.minusMonths(1).withDayOfMonth(1).toString();
            default -> value;
        };
    }

    /**
     * 解析相对日期关键词为日期范围 [start, end]。
     * 返回 null 表示非范围关键词。
     */
    private String[] resolveRelativeDateRange(String value) {
        if (value == null) return null;
        java.time.LocalDate today = java.time.LocalDate.now();
        return switch (value) {
            case "${today}", "today", "今天" -> new String[]{today + " 00:00:00", today + " 23:59:59"};
            case "yesterday", "昨天" -> {
                var d = today.minusDays(1);
                yield new String[]{d + " 00:00:00", d + " 23:59:59"};
            }
            case "this week", "本周" -> {
                var start = today.with(java.time.DayOfWeek.MONDAY);
                var end = start.plusDays(6);
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "last week", "上周" -> {
                var start = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
                var end = start.plusDays(6);
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "this month", "本月" -> {
                var start = today.withDayOfMonth(1);
                var end = today.withDayOfMonth(today.lengthOfMonth());
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "last month", "上月" -> {
                var lastMonth = today.minusMonths(1);
                var start = lastMonth.withDayOfMonth(1);
                var end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            default -> null;
        };
    }

    /**
     * 自定义字段 IN 操作符的最大值数量上限
     */
    private static final int CF_IN_VALUES_MAX_SIZE = 100;

    /**
     * 应用自定义字段筛选（通过 EXISTS 子查询关联 custom_field_value EAV 表）
     */
    private void applyCustomFieldFilter(QueryWrapper<Issue> wrapper, String cfKey, String operator, List<String> values) {
        if (!CF_KEY_PATTERN.matcher(cfKey).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的自定义字段名: " + cfKey);
        }

        // cfKey 是字段 ID（snowflake ID），通过 EXISTS 子查询关联 custom_field_value 表
        switch (operator) {
            case "eq" -> wrapper.apply(
                    "EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND cfv.value = {1})",
                    Long.parseLong(cfKey), values.get(0));
            case "neq" -> wrapper.apply(
                    "NOT EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND cfv.value = {1})",
                    Long.parseLong(cfKey), values.get(0));
            case "in" -> applyCustomFieldInFilter(wrapper, cfKey, values, false);
            case "not_in" -> applyCustomFieldInFilter(wrapper, cfKey, values, true);
            case "contains" -> wrapper.apply(
                    "EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND cfv.value LIKE {1} ESCAPE '\\')",
                    Long.parseLong(cfKey), "%" + SqlUtils.escapeLikePattern(values.get(0)) + "%");
            case "is_empty" -> wrapper.apply(
                    "NOT EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND cfv.value IS NOT NULL AND cfv.value != '')",
                    Long.parseLong(cfKey));
            case "is_not_empty" -> wrapper.apply(
                    "EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND cfv.value IS NOT NULL AND cfv.value != '')",
                    Long.parseLong(cfKey));
        }
    }

    /**
     * 参数化实现自定义字段的 IN / NOT IN 筛选。
     * 使用 MyBatis-Plus apply() 的编号占位符 {0}, {1}, {2}... 确保所有值通过 PreparedStatement 参数传递，
     * 彻底杜绝 SQL 注入。
     *
     * @param wrapper  查询包装器
     * @param cfKey    自定义字段 ID（已校验格式）
     * @param values   筛选值列表
     * @param negate   true 表示 NOT IN（NOT EXISTS），false 表示 IN（EXISTS）
     */
    private void applyCustomFieldInFilter(QueryWrapper<Issue> wrapper, String cfKey, List<String> values, boolean negate) {
        if (values == null || values.isEmpty()) {
            if (negate) {
                // NOT IN 空列表 = 匹配所有，不添加条件
                return;
            } else {
                // IN 空列表 = 匹配无结果
                wrapper.apply("1 = 0");
                return;
            }
        }

        if (values.size() > CF_IN_VALUES_MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "自定义字段 IN 筛选值数量不能超过 " + CF_IN_VALUES_MAX_SIZE + " 个");
        }

        // 构建参数化 SQL：{0} 为 fieldId，{1}..{N} 为各个 value
        // 例如 3 个值: "EXISTS (SELECT 1 FROM ... AND (cfv.value = {1} OR cfv.value = {2} OR cfv.value = {3}))"
        StringBuilder sql = new StringBuilder();
        sql.append(negate ? "NOT " : "");
        sql.append("EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND (");

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("cfv.value = {").append(i + 1).append("}");
        }
        sql.append("))");

        // 组装参数数组: [fieldId, value1, value2, ...]
        List<Object> params = new ArrayList<>(values.size() + 1);
        params.add(Long.parseLong(cfKey));
        params.addAll(values);

        wrapper.apply(sql.toString(), params.toArray());
    }

    /**
     * 校验状态 code 列表，防止 SQL 注入
     */
    private void validateStatusCodes(List<String> values) {
        for (String code : values) {
            if (!STATUS_CODE_PATTERN.matcher(code).matches()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的状态 code: " + code);
            }
        }
    }

    /**
     * 将已校验的状态 code 列表转为安全的 SQL IN 子句
     * 注意：调用前必须先通过 validateStatusCodes() 校验
     */
    private String toSafeStatusCodeList(List<String> values) {
        return values.stream()
                .map(v -> "'" + v + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("''");
    }

    private String camelToSnake(String camel) {
        if (camel == null) return "updated_at";
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

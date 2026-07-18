package com.trackflow.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
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
     * 计数查询（不分页，只返回匹配数量）
     */
    public long count(List<Map<String, Object>> filters) {
        QueryWrapper<Issue> wrapper = buildWrapper(filters);
        return issueMapper.selectCount(wrapper);
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
     * 应用排序条件（白名单校验）
     */
    private void applySortCriteria(QueryWrapper<Issue> wrapper, List<Map<String, String>> sortCriteria) {
        if (sortCriteria != null && !sortCriteria.isEmpty()) {
            for (Map<String, String> sort : sortCriteria) {
                String field = camelToSnake(sort.get("field"));
                if (!ALLOWED_SORT_FIELDS.contains(field)) {
                    log.warn("非法排序字段被拦截: {}", field);
                    continue; // 忽略非法字段，不中断查询
                }
                String direction = sort.getOrDefault("direction", "desc");
                if ("asc".equalsIgnoreCase(direction)) {
                    wrapper.orderByAsc(field);
                } else {
                    wrapper.orderByDesc(field);
                }
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
                case "dueDate" -> applyDateFilter(wrapper, "due_date", operator, values);
                case "createdAt" -> applyDateFilter(wrapper, "created_at", operator, values);
                case "updatedAt" -> applyDateFilter(wrapper, "updated_at", operator, values);
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
                if ("${today}".equals(s)) {
                    return java.time.LocalDate.now().toString();
                }
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
        String kw = values.get(0);
        wrapper.and(w -> w
                .like("title", kw)
                .or().like("description", kw)
                .or().like("issue_key", kw)
        );
    }

    private void applyDateFilter(QueryWrapper<Issue> wrapper, String column, String operator, List<String> values) {
        switch (operator) {
            case "eq" -> wrapper.eq(column, values.get(0));
            case "gt" -> wrapper.gt(column, values.get(0));
            case "gte" -> wrapper.ge(column, values.get(0));
            case "lt" -> wrapper.lt(column, values.get(0));
            case "lte" -> wrapper.le(column, values.get(0));
            case "between" -> {
                if (values.size() >= 2) {
                    wrapper.between(column, values.get(0), values.get(1));
                }
            }
        }
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
                    "EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = {0} AND cfv.value LIKE {1})",
                    Long.parseLong(cfKey), "%" + values.get(0) + "%");
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

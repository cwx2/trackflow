package com.trackflow.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    /**
     * 执行筛选查询
     */
    public Page<Issue> execute(List<Map<String, Object>> filters, int page, int pageSize, List<Map<String, String>> sortCriteria) {
        Page<Issue> pageObj = new Page<>(page, pageSize);
        QueryWrapper<Issue> wrapper = buildWrapper(filters);

        // 排序
        if (sortCriteria != null && !sortCriteria.isEmpty()) {
            for (Map<String, String> sort : sortCriteria) {
                String field = camelToSnake(sort.get("field"));
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

            // 解析动态变量
            List<String> values = resolveValues(value, currentUserId);

            switch (field) {
                case "project" -> applyFilter(wrapper, "project_id", operator, values);
                case "status" -> applyStatusFilter(wrapper, operator, values);
                case "priority" -> applyFilter(wrapper, "priority", operator, values);
                case "assignee" -> applyUserFilter(wrapper, "assignee_id", operator, values, currentUserId);
                case "reporter" -> applyUserFilter(wrapper, "reporter_id", operator, values, currentUserId);
                case "type" -> applyFilter(wrapper, "issue_type", operator, values);
                case "sprint" -> applyFilter(wrapper, "sprint_id", operator, values);
                case "keyword" -> applyKeywordFilter(wrapper, values);
                case "dueDate" -> applyDateFilter(wrapper, "due_date", operator, values);
                case "createdAt" -> applyDateFilter(wrapper, "created_at", operator, values);
                case "updatedAt" -> applyDateFilter(wrapper, "updated_at", operator, values);
                default -> {
                    // 自定义字段: custom_fields->>'fieldKey'
                    if (field.startsWith("cf.") || field.startsWith("customField.")) {
                        String cfKey = field.contains(".") ? field.substring(field.indexOf('.') + 1) : field;
                        applyCustomFieldFilter(wrapper, cfKey, operator, values);
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
            case "eq", "in" -> wrapper.inSql("status_id",
                    "SELECT id FROM issue_status WHERE code IN (" + toSqlList(values) + ")");
            case "not_in" -> wrapper.notInSql("status_id",
                    "SELECT id FROM issue_status WHERE code IN (" + toSqlList(values) + ")");
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

    private void applyCustomFieldFilter(QueryWrapper<Issue> wrapper, String cfKey, String operator, List<String> values) {
        String jsonPath = "custom_fields->>'" + cfKey + "'";
        switch (operator) {
            case "eq" -> wrapper.apply(jsonPath + " = {0}", values.get(0));
            case "neq" -> wrapper.apply(jsonPath + " != {0}", values.get(0));
            case "contains" -> wrapper.apply(jsonPath + " LIKE {0}", "%" + values.get(0) + "%");
            case "is_empty" -> wrapper.apply(jsonPath + " IS NULL");
            case "is_not_empty" -> wrapper.apply(jsonPath + " IS NOT NULL");
        }
    }

    private String toSqlList(List<String> values) {
        return values.stream().map(v -> "'" + v.replace("'", "''") + "'").reduce((a, b) -> a + "," + b).orElse("''");
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

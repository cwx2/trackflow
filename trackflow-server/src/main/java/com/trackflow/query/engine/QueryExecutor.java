package com.trackflow.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.SqlUtils;
import com.trackflow.issue.util.IssuePriorityHelper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.customfield.service.CustomFieldSortHelper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
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
    private final CustomFieldDefinitionMapper customFieldDefinitionMapper;
    private final CustomFieldOptionMapper customFieldOptionMapper;
    private final SprintMapper sprintMapper;

    /**
     * 允许排序的字段白名单（数据库列名）
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "issue_key", "title", "status_id", "priority",
            "assignee_id", "reporter_id", "created_at", "updated_at",
            "due_date", "sprint_id", "issue_type", "project_id", "project"
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
            "open", "closed", "relative"
    );

    /**
     * 执行筛选查询（无项目权限过滤）。
     * <p>
     * ⚠️ <b>安全警告</b>：此方法不过滤项目权限，调用方必须确保 filters 中已包含
     * 有效的 project 约束，或调用者已通过其他方式完成权限校验。
     * <p>
     * 推荐优先使用 {@link #executeWithProjectFilter}，其内置了 accessibleProjectIds 过滤。
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
        return countWithProjectFilter(filters, accessibleProjectIds, false);
    }

    /**
     * 计数查询（带项目成员过滤 + 隐藏已解决）
     */
    public long countWithProjectFilter(List<Map<String, Object>> filters, List<Long> accessibleProjectIds, boolean hideResolved) {
        QueryWrapper<Issue> wrapper = buildWrapper(filters);
        if (accessibleProjectIds != null) {
            if (accessibleProjectIds.isEmpty()) {
                return 0;
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
                    // "project" 是前端列 key 的别名，映射到实际数据库列 project_id
                    if ("project".equals(columnName)) {
                        columnName = "project_id";
                    }
                    if (!ALLOWED_SORT_FIELDS.contains(columnName)) {
                        log.warn("非法排序字段被拦截: {}", field);
                        continue;
                    }
                    // 使用 IssuePriorityHelper 统一优先级排序逻辑
                    // 用户 "desc" = 最高优先级在前 = CASE ASC；用户 "asc" = 最低优先级在前 = CASE DESC
                    if ("priority".equals(columnName)) {
                        if (asc) {
                            // 用户要求 asc = 低优先级在前（Low → Normal → High → Critical）
                            wrapper.orderByDesc(IssuePriorityHelper.PRIORITY_ORDER_EXPR);
                        } else {
                            // 用户要求 desc = 高优先级在前（Critical → High → Normal → Low）
                            wrapper.orderByAsc(IssuePriorityHelper.PRIORITY_ORDER_EXPR);
                        }
                    } else {
                        if (asc) {
                            wrapper.orderByAsc(columnName);
                        } else {
                            wrapper.orderByDesc(columnName);
                        }
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

        // 预扫描 filters 提取项目 ID 上下文（用于解析 ${currentSprint}）
        List<Long> contextProjectIds = extractProjectIds(filters);

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
            List<String> values = resolveValues(value, currentUserId, contextProjectIds);

            // 当 ${currentSprint} 扩展为多个 ID 且原操作符为 eq 时，升级为 in
            if ("sprint".equals(field) && "eq".equals(operator) && values.size() > 1) {
                operator = "in";
            }

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
                        // 如果 cfKey 不是纯数字 ID，尝试通过名称或别名查找字段
                        String resolvedFieldId = resolveCustomFieldId(cfKey);
                        if (resolvedFieldId != null) {
                            applyCustomFieldFilter(wrapper, resolvedFieldId, operator, values);
                        } else {
                            log.warn("未找到自定义字段（按名称/别名）: {}", cfKey);
                        }
                    } else {
                        log.warn("未知的筛选字段被忽略: {}", field);
                    }
                }
            }
        }

        return wrapper;
    }

    /**
     * 从 filters 中提取 project 字段的值作为上下文项目 ID 列表。
     * 用于解析 ${currentSprint} 时确定在哪些项目中查找活跃 Sprint。
     */
    @SuppressWarnings("unchecked")
    private List<Long> extractProjectIds(List<Map<String, Object>> filters) {
        List<Long> projectIds = new ArrayList<>();
        for (Map<String, Object> filter : filters) {
            String field = (String) filter.get("field");
            if ("project".equals(field)) {
                Object value = filter.get("value");
                if (value instanceof List) {
                    for (Object v : (List<Object>) value) {
                        try {
                            projectIds.add(Long.parseLong(String.valueOf(v)));
                        } catch (NumberFormatException ignored) {
                            // skip non-numeric values like ${currentUser}
                        }
                    }
                }
            }
        }
        return projectIds;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveValues(Object value, Long currentUserId, List<Long> contextProjectIds) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object v : (List<Object>) value) {
                String s = String.valueOf(v);
                if ("${currentUser}".equals(s) && currentUserId != null) {
                    result.add(String.valueOf(currentUserId));
                } else if ("${currentSprint}".equals(s)) {
                    // 解析当前活跃 Sprint：查找 contextProjectIds 中状态为 ACTIVE 的 Sprint
                    List<Long> activeSprintIds = resolveCurrentSprintIds(contextProjectIds);
                    if (activeSprintIds.isEmpty()) {
                        // 无活跃 Sprint 时，使用一个不可能匹配的 ID，确保查询结果为空
                        result.add("-1");
                    } else {
                        for (Long sprintId : activeSprintIds) {
                            result.add(String.valueOf(sprintId));
                        }
                    }
                } else {
                    // ${today} kept as-is — resolved later in applyDateFilter
                    result.add(s);
                }
            }
            return result;
        }
        return List.of(String.valueOf(value));
    }

    /**
     * 查找指定项目中当前活跃的 Sprint ID 列表。
     * 逻辑参考 YouTrack 的 {Current sprint} 语义：
     * 1. 优先匹配 status = ACTIVE 的 Sprint
     * 2. 如果没有 ACTIVE，回退到最早的 PLANNED Sprint（尚未开始的下一个）
     * 3. 如果 contextProjectIds 为空，查找所有项目的活跃 Sprint
     */
    private List<Long> resolveCurrentSprintIds(List<Long> contextProjectIds) {
        LambdaQueryWrapper<Sprint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sprint::getStatus, SprintStatus.ACTIVE);
        if (contextProjectIds != null && !contextProjectIds.isEmpty()) {
            wrapper.in(Sprint::getProjectId, contextProjectIds);
        }
        List<Sprint> activeSprints = sprintMapper.selectList(wrapper);
        if (!activeSprints.isEmpty()) {
            return activeSprints.stream().map(Sprint::getId).toList();
        }

        // 回退：查找最早的 PLANNED Sprint
        LambdaQueryWrapper<Sprint> plannedWrapper = new LambdaQueryWrapper<>();
        plannedWrapper.eq(Sprint::getStatus, SprintStatus.PLANNED);
        if (contextProjectIds != null && !contextProjectIds.isEmpty()) {
            plannedWrapper.in(Sprint::getProjectId, contextProjectIds);
        }
        plannedWrapper.orderByAsc(Sprint::getStartDate);
        plannedWrapper.last("LIMIT 1");
        List<Sprint> plannedSprints = sprintMapper.selectList(plannedWrapper);
        if (!plannedSprints.isEmpty()) {
            return plannedSprints.stream().map(Sprint::getId).toList();
        }

        // 第三级回退：当项目无活跃或计划中 Sprint 时，回退到最近完成的、且含有未关闭工单的 Sprint
        // 这样 "当前迭代" 查询仍能显示遗留在已完成 Sprint 中的未关闭工单
        LambdaQueryWrapper<Sprint> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(Sprint::getStatus, SprintStatus.COMPLETED);
        if (contextProjectIds != null && !contextProjectIds.isEmpty()) {
            completedWrapper.in(Sprint::getProjectId, contextProjectIds);
        }
        // 只选有未关闭工单的 Sprint（通过 EXISTS 子查询过滤空 Sprint）
        completedWrapper.exists(
                "SELECT 1 FROM issue i JOIN issue_status ist ON i.status_id = ist.id " +
                "WHERE i.sprint_id = sprint.id AND ist.is_closed = false AND i.deleted_at IS NULL"
        );
        completedWrapper.orderByDesc(Sprint::getEndDate);
        completedWrapper.last("LIMIT 1");
        List<Sprint> completedSprints = sprintMapper.selectList(completedWrapper);
        if (!completedSprints.isEmpty()) {
            return completedSprints.stream().map(Sprint::getId).toList();
        }

        return List.of();
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
                .apply("title ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                // description 子串匹配：利用 idx_issue_description_trgm trigram GIN 索引
                .apply("description ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                // issue_key 匹配
                .apply("issue_key ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                // assignee 名称匹配
                .apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name ILIKE {0} ESCAPE '\\' OR username ILIKE {0} ESCAPE '\\')", likePattern)
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
                    // 所有值已通过 Long.parseLong 转换，使用 inSql 避免字符串拼接
                    String idList = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    wrapper.apply(
                            "EXISTS (SELECT 1 FROM issue_comment ic WHERE ic.issue_id = issue.id AND ic.user_id IN ("
                                    + idList + ") AND ic.deleted_at IS NULL)");
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
            case "eq", "relative" -> {
                // For relative keywords that represent ranges (e.g. "this_week"), use ge + le
                String[] range = resolveRelativeDateRange(values.get(0));
                if (range != null) {
                    wrapper.apply(column + " >= {0}::timestamp", range[0]);
                    wrapper.apply(column + " <= {0}::timestamp", range[1]);
                } else {
                    wrapper.apply(column + " >= {0}::timestamp", resolvedValues.get(0) + " 00:00:00");
                    wrapper.apply(column + " < {0}::timestamp", resolvedValues.get(0) + " 23:59:59");
                }
            }
            case "gt" -> wrapper.apply(column + " > {0}::timestamp", resolvedValues.get(0) + " 23:59:59");
            case "gte" -> wrapper.apply(column + " >= {0}::timestamp", resolvedValues.get(0) + " 00:00:00");
            case "lt" -> wrapper.apply(column + " < {0}::timestamp", resolvedValues.get(0) + " 00:00:00");
            case "lte" -> wrapper.apply(column + " <= {0}::timestamp", resolvedValues.get(0) + " 23:59:59");
            case "between" -> {
                if (resolvedValues.size() >= 2) {
                    wrapper.apply(column + " >= {0}::timestamp", resolvedValues.get(0) + " 00:00:00");
                    wrapper.apply(column + " <= {0}::timestamp", resolvedValues.get(1) + " 23:59:59");
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
            case "this week", "this_week", "本周" -> today.with(java.time.DayOfWeek.MONDAY).toString();
            case "last week", "last_week", "上周" -> today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY).toString();
            case "this month", "this_month", "本月" -> today.withDayOfMonth(1).toString();
            case "last month", "last_month", "上月" -> today.minusMonths(1).withDayOfMonth(1).toString();
            case "last_7_days" -> today.minusDays(6).toString();
            case "last_30_days" -> today.minusDays(29).toString();
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
            case "this week", "this_week", "本周" -> {
                var start = today.with(java.time.DayOfWeek.MONDAY);
                var end = start.plusDays(6);
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "last week", "last_week", "上周" -> {
                var start = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
                var end = start.plusDays(6);
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "this month", "this_month", "本月" -> {
                var start = today.withDayOfMonth(1);
                var end = today.withDayOfMonth(today.lengthOfMonth());
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "last month", "last_month", "上月" -> {
                var lastMonth = today.minusMonths(1);
                var start = lastMonth.withDayOfMonth(1);
                var end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
                yield new String[]{start + " 00:00:00", end + " 23:59:59"};
            }
            case "last_7_days" -> {
                var start = today.minusDays(6);
                yield new String[]{start + " 00:00:00", today + " 23:59:59"};
            }
            case "last_30_days" -> {
                var start = today.minusDays(29);
                yield new String[]{start + " 00:00:00", today + " 23:59:59"};
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
            case "between" -> applyCustomFieldBetweenFilter(wrapper, cfKey, values);
        }
    }

    /**
     * 枚举类型自定义字段的范围查询（between 操作符）。
     * 根据选项的 position 排序确定范围，将 "值A .. 值B" 转换为匹配所有位于 A 和 B 之间选项值的查询。
     *
     * 支持字段类型：list、version（拥有有序选项的枚举类型）
     * 不支持字段类型：user、state
     *
     * @param wrapper 查询包装器
     * @param cfKey   自定义字段 ID（已校验格式）
     * @param values  两个值的列表 [起始值, 结束值]
     */
    private void applyCustomFieldBetweenFilter(QueryWrapper<Issue> wrapper, String cfKey, List<String> values) {
        if (values == null || values.size() < 2) {
            log.warn("自定义字段 between 操作符需要两个值，实际收到: {}", values);
            return;
        }

        Long fieldId = Long.parseLong(cfKey);
        String startValue = values.get(0);
        String endValue = values.get(1);

        // 查找该字段的所有全局选项（按 position 排序）
        List<CustomFieldOption> options = customFieldOptionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .isNull(CustomFieldOption::getProjectId)
                        .orderByAsc(CustomFieldOption::getPosition)
        );

        if (options.isEmpty()) {
            log.warn("自定义字段 {} 没有选项，范围查询无效", cfKey);
            wrapper.apply("1 = 0");
            return;
        }

        // 查找起始值和结束值的 position（先按选项 label 匹配，再按选项 ID 匹配）
        Integer startPos = null;
        Integer endPos = null;
        for (CustomFieldOption option : options) {
            // 先尝试按 label（option.value）匹配
            if (option.getValue().equals(startValue)) {
                startPos = option.getPosition();
            }
            if (option.getValue().equals(endValue)) {
                endPos = option.getPosition();
            }
        }

        // 如果按 label 未匹配，尝试按 option ID 匹配（支持 saved query 传入 ID 的场景）
        if (startPos == null || endPos == null) {
            for (CustomFieldOption option : options) {
                if (startPos == null && String.valueOf(option.getId()).equals(startValue)) {
                    startPos = option.getPosition();
                }
                if (endPos == null && String.valueOf(option.getId()).equals(endValue)) {
                    endPos = option.getPosition();
                }
            }
        }

        if (startPos == null || endPos == null) {
            log.warn("自定义字段 {} 范围查询的边界值无效: {} .. {}", cfKey, startValue, endValue);
            wrapper.apply("1 = 0");
            return;
        }

        // 确保 startPos <= endPos（如果反向则交换）
        int minPos = Math.min(startPos, endPos);
        int maxPos = Math.max(startPos, endPos);

        // 收集 position 在范围内的所有选项 ID（custom_field_value.value 存储的是选项 ID）
        List<String> rangeOptionIds = options.stream()
                .filter(opt -> opt.getPosition() != null
                        && opt.getPosition() >= minPos
                        && opt.getPosition() <= maxPos)
                .map(opt -> String.valueOf(opt.getId()))
                .collect(Collectors.toList());

        if (rangeOptionIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }

        // 转换为 IN 查询（复用已有的参数化 IN 逻辑）
        applyCustomFieldInFilter(wrapper, cfKey, rangeOptionIds, false);
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

    /**
     * 解析自定义字段标识符，支持以下格式：
     * 1. 纯数字 ID：直接返回（如 "1234567890"）
     * 2. 字段名称：通过 name 匹配
     * 3. 字段别名：通过 aliases 列中的逗号分隔值匹配
     *
     * @param cfKey 字段标识符（ID、名称或别名）
     * @return 解析后的字段 ID，找不到返回 null
     */
    private String resolveCustomFieldId(String cfKey) {
        if (cfKey == null || cfKey.isBlank()) {
            return null;
        }

        // 如果是纯数字，假定是字段 ID，直接返回（符合 CF_KEY_PATTERN 校验）
        if (cfKey.matches("^\\d+$")) {
            return cfKey;
        }

        // 先尝试精确匹配字段名称
        CustomFieldDefinition byName = customFieldDefinitionMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getName, cfKey)
                        .last("LIMIT 1")
        );
        if (byName != null) {
            return String.valueOf(byName.getId());
        }

        // 再尝试匹配别名（aliases 存储为逗号分隔字符串，如 "for,assigned to"）
        // 使用数据库 LIKE 匹配（考虑性能，这里假设自定义字段数量不会很多）
        List<CustomFieldDefinition> allFields = customFieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .isNotNull(CustomFieldDefinition::getAliases)
                        .ne(CustomFieldDefinition::getAliases, "")
        );

        String searchKey = cfKey.toLowerCase().trim();
        for (CustomFieldDefinition field : allFields) {
            String aliases = field.getAliases();
            if (aliases != null && !aliases.isBlank()) {
                String[] aliasArray = aliases.split(",");
                for (String alias : aliasArray) {
                    if (alias.trim().equalsIgnoreCase(searchKey)) {
                        return String.valueOf(field.getId());
                    }
                }
            }
        }

        // 找不到匹配的字段
        return null;
    }
}

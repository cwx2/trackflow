package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.service.MinioService;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.dto.CreateIssueDTO;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.dto.UpdateIssueDTO;
import com.trackflow.issue.entity.*;
import com.trackflow.issue.mapper.*;
import com.trackflow.project.service.ProjectService;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.vo.*;
import com.trackflow.workflow.service.TransitionActionEngine;
import com.trackflow.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueActivityMapper activityMapper;
    private final com.trackflow.sprint.mapper.SprintMapper sprintMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;
    private final IssueConverter issueConverter;
    private final IssueTagService tagService;
    private final PermissionService permissionService;
    private final TransitionActionEngine transitionActionEngine;
    private final WorkflowService workflowService;
    private final IssueNotificationHelper notificationHelper;
    private final StatusCacheHelper statusCacheHelper;
    private final CustomFieldService customFieldService;

    /**
     * 创建 Issue
     */
    @Transactional
    public Issue create(CreateIssueDTO dto) {
        // 归档项目不允许创建工单
        projectService.assertProjectActive(dto.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 生成 Issue Key
        int seq = projectService.nextIssueSequence(dto.getProjectId());
        var project = projectService.getById(dto.getProjectId());
        String issueKey = project.getKey() + "-" + seq;

        // 获取默认状态
        IssueStatus defaultStatus = statusMapper.selectOne(
                new LambdaQueryWrapper<IssueStatus>().eq(IssueStatus::getIsDefault, true)
        );

        Issue issue = new Issue();
        issue.setProjectId(dto.getProjectId());
        issue.setIssueKey(issueKey);
        issue.setTitle(dto.getTitle());
        issue.setDescription(dto.getDescription());
        issue.setIssueType(dto.getIssueType() != null ? dto.getIssueType() : "Task");
        issue.setStatusId(defaultStatus != null ? defaultStatus.getId() : 1L);
        issue.setPriority(dto.getPriority() != null ? dto.getPriority() : "Normal");
        issue.setAssigneeId(dto.getAssigneeId());
        issue.setReporterId(currentUserId);
        issue.setSprintId(dto.getSprintId());
        issue.setParentId(dto.getParentId());
        // 创建时如果指定了 parentId，进行环路检测（虽然新工单没有子工单不会形成环路，但验证 parent 存在且有效）
        if (dto.getParentId() != null) {
            Issue parentIssue = issueMapper.selectById(dto.getParentId());
            if (parentIssue == null || parentIssue.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的父工单不存在");
            }
        }
        issue.setDueDate(dto.getDueDate());
        issue.setEstimatedHours(dto.getEstimatedHours());
        issue.setCreatedBy(currentUserId);

        if (dto.getCustomFields() != null) {
            try {
                issue.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (JsonProcessingException e) {
                issue.setCustomFields("{}");
            }
        }

        issueMapper.insert(issue);

        // 保存自定义字段值到 EAV 表（带验证）
        if (dto.getCustomFields() != null && !dto.getCustomFields().isEmpty()) {
            Map<Long, String> fieldValues = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : dto.getCustomFields().entrySet()) {
                try {
                    fieldValues.put(Long.parseLong(entry.getKey()),
                            entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
                } catch (NumberFormatException ignored) {}
            }
            customFieldService.saveValues(issue.getId(), fieldValues,
                    issue.getIssueType(), issue.getProjectId());
        }

        // 记录活动
        recordActivity(issue.getId(), currentUserId, "created", null, null, null);

        // 通知被分配人（若创建时指定了 assignee）
        notificationHelper.notifyCreated(issue, currentUserId);

        return issue;
    }

    /**
     * Issue 列表（支持多条件筛选）
     */
    public Page<Issue> list(Page<Issue> page, Long projectId, Long statusId, String priority,
                            Long assigneeId, Long reporterId, Long sprintId, String issueType,
                            String keyword) {
        return list(page, projectId, statusId, priority, assigneeId, reporterId, sprintId, issueType, keyword,
                null, null, null, null, null);
    }

    /**
     * Issue 列表（接受 IssueQuery，完整筛选支持）
     * 强制按用户所属项目过滤：如果指定了 projectId，校验成员关系；如果未指定，自动限定为所属项目。
     */
    public Page<Issue> listByQuery(IssueQuery query) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (query.getProjectId() != null) {
            // 指定了 projectId，校验成员关系
            projectService.assertProjectMember(currentUserId, query.getProjectId());
            wrapper.eq("project_id", query.getProjectId());
        } else {
            // 未指定 projectId，自动限定为用户所属项目
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                // 非系统管理员：限定项目范围
                if (accessibleProjectIds.isEmpty()) {
                    return new Page<>();
                }
                wrapper.in("project_id", accessibleProjectIds);
            }
            // accessibleProjectIds == null 表示系统管理员，不加限制
        }

        // statusId: supports single or comma-separated
        applyFilter(wrapper, "status_id", query.getStatusId(), true);
        // priority: supports single or comma-separated
        applyFilter(wrapper, "priority", query.getPriority(), false);
        // assigneeId: supports single or comma-separated
        applyFilter(wrapper, "assignee_id", query.getAssigneeId(), true);
        if (query.getReporterId() != null) wrapper.eq("reporter_id", query.getReporterId());
        // sprintId: supports single or comma-separated
        applyFilter(wrapper, "sprint_id", query.getSprintId(), true);
        // issueType: supports single or comma-separated
        applyFilter(wrapper, "issue_type", query.getIssueType(), false);

        // Negative filters
        applyNegativeFilter(wrapper, "status_id", query.getStatusIdNot(), true);
        applyNegativeFilter(wrapper, "priority", query.getPriorityNot(), false);
        applyNegativeFilter(wrapper, "assignee_id", query.getAssigneeIdNot(), true);
        applyNegativeFilter(wrapper, "sprint_id", query.getSprintIdNot(), true);
        applyNegativeFilter(wrapper, "issue_type", query.getIssueTypeNot(), false);

        // Special filters: overdue, dueSoon, reportedByMe (all exclude done/cancelled statuses)
        boolean needClosedExclusion = "true".equals(query.getOverdue())
                || "true".equals(query.getDueSoon())
                || "true".equals(query.getReportedByMe());

        if (needClosedExclusion) {
            List<IssueStatus> allStatuses = statusMapper.selectList(null);
            List<Long> closedIds = allStatuses.stream()
                    .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
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

        // reportedByMe: reporter_id = current user
        if ("true".equals(query.getReportedByMe())) {
            wrapper.eq("reporter_id", currentUserId);
        }

        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            applyKeywordFilter(wrapper, keyword);
        }

        wrapper.orderByDesc("updated_at");
        return issueMapper.selectPage(query.toPage(), wrapper);
    }

    private void applyFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
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
        List<?> values = isNumeric
                ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        wrapper.notIn(column, values);
    }

    /**
     * 关键词过滤：匹配 title、description、issue_key 或 assignee 的 display_name/username。
     * 使用参数化查询防止 SQL 注入。
     */
    private void applyKeywordFilter(QueryWrapper<Issue> wrapper, String keyword) {
        String likePattern = "%" + keyword + "%";
        wrapper.and(w -> w
                .like("title", keyword)
                .or()
                .like("description", keyword)
                .or()
                .like("issue_key", keyword)
                .or()
                .apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name LIKE {0} OR username LIKE {0})", likePattern)
        );
    }

    /**
     * Issue 列表（支持正向+否定筛选，支持逗号分隔多值）
     * 强制数据隔离：指定 projectId 时校验成员关系，未指定时限定为用户所属项目。
     */
    public Page<Issue> list(Page<Issue> page, Long projectId, Long statusId, String priority,
                            Long assigneeId, Long reporterId, Long sprintId, String issueType,
                            String keyword,
                            String statusIdNot, String priorityNot, String assigneeIdNot,
                            String sprintIdNot, String issueTypeNot) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        // 数据隔离：与 listByQuery 保持一致
        if (projectId != null) {
            projectService.assertProjectMember(currentUserId, projectId);
            wrapper.eq("project_id", projectId);
        } else {
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                if (accessibleProjectIds.isEmpty()) {
                    return new Page<>();
                }
                wrapper.in("project_id", accessibleProjectIds);
            }
        }
        if (statusId != null) wrapper.eq("status_id", statusId);
        if (priority != null) {
            // Support comma-separated values (any_of)
            if (priority.contains(",")) {
                wrapper.in("priority", java.util.Arrays.asList(priority.split(",")));
            } else {
                wrapper.eq("priority", priority);
            }
        }
        if (assigneeId != null) wrapper.eq("assignee_id", assigneeId);
        if (reporterId != null) wrapper.eq("reporter_id", reporterId);
        if (sprintId != null) wrapper.eq("sprint_id", sprintId);
        if (issueType != null) {
            if (issueType.contains(",")) {
                wrapper.in("issue_type", java.util.Arrays.asList(issueType.split(",")));
            } else {
                wrapper.eq("issue_type", issueType);
            }
        }

        // Negative filters
        if (statusIdNot != null && !statusIdNot.isBlank()) {
            List<Long> notIds = java.util.Arrays.stream(statusIdNot.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            wrapper.notIn("status_id", notIds);
        }
        if (priorityNot != null && !priorityNot.isBlank()) {
            wrapper.notIn("priority", java.util.Arrays.asList(priorityNot.split(",")));
        }
        if (assigneeIdNot != null && !assigneeIdNot.isBlank()) {
            List<Long> notIds = java.util.Arrays.stream(assigneeIdNot.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            wrapper.notIn("assignee_id", notIds);
        }
        if (sprintIdNot != null && !sprintIdNot.isBlank()) {
            List<Long> notIds = java.util.Arrays.stream(sprintIdNot.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            wrapper.notIn("sprint_id", notIds);
        }
        if (issueTypeNot != null && !issueTypeNot.isBlank()) {
            wrapper.notIn("issue_type", java.util.Arrays.asList(issueTypeNot.split(",")));
        }

        if (keyword != null && !keyword.isBlank()) {
            applyKeywordFilter(wrapper, keyword);
        }

        wrapper.orderByDesc("updated_at");
        return issueMapper.selectPage(page, wrapper);
    }

    /**
     * Issue 详情
     */
    public Issue getById(Long id) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null || issue.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return issue;
    }

    /**
     * 仅获取 Issue 所属的 projectId（轻量查询，供 @PreAuthorize SpEL 使用）
     * 只查 project_id 单列，避免在 SpEL 中全量加载 Issue 对象
     */
    public Long getProjectId(Long issueId) {
        Issue issue = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getProjectId)
                        .eq(Issue::getId, issueId)
                        .isNull(Issue::getDeletedAt)
        );
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return issue.getProjectId();
    }

    /**
     * 获取 Issue 详情（带项目成员校验）
     * 校验当前用户是否有权访问该工单所属的项目
     */
    public Issue getByIdWithAccessCheck(Long id) {
        Issue issue = getById(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(currentUserId, issue.getProjectId());
        return issue;
    }

    /**
     * 通过 issueKey 获取（带项目成员校验）
     */
    public Issue getByKeyWithAccessCheck(String issueKey) {
        Issue issue = getByKey(issueKey);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(currentUserId, issue.getProjectId());
        return issue;
    }

    /**
     * 通过 issueKey 获取
     */
    public Issue getByKey(String issueKey) {
        Issue issue = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>().eq(Issue::getIssueKey, issueKey).isNull(Issue::getDeletedAt)
        );
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return issue;
    }

    /**
     * 更新 Issue
     */
    @Transactional
    public Issue update(Long id, UpdateIssueDTO dto) {
        Issue issue = getById(id);
        // 归档项目不允许编辑工单
        projectService.assertProjectActive(issue.getProjectId());

        // 乐观锁版本校验：前端携带 version 时检查是否与 DB 一致
        if (dto.getVersion() != null && !dto.getVersion().equals(issue.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该工单已被其他人修改，请刷新页面后重试");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        if (dto.getTitle() != null) {
            recordActivity(id, currentUserId, "updated", "title", issue.getTitle(), dto.getTitle());
            issue.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            recordActivity(id, currentUserId, "updated", "description",
                    issue.getDescription() != null ? "（已有内容）" : null,
                    dto.getDescription() != null ? "（已更新）" : null);
            issue.setDescription(dto.getDescription());
        }
        if (dto.getIssueType() != null) {
            recordActivity(id, currentUserId, "updated", "issue_type", issue.getIssueType(), dto.getIssueType());
            issue.setIssueType(dto.getIssueType());
        }
        if (dto.getPriority() != null) {
            recordActivity(id, currentUserId, "updated", "priority", issue.getPriority(), dto.getPriority());
            issue.setPriority(dto.getPriority());
        }
        if (dto.getAssigneeId() != null) {
            recordActivity(id, currentUserId, "assigned", "assignee",
                    issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null,
                    String.valueOf(dto.getAssigneeId()));
            Long oldAssigneeId = issue.getAssigneeId();
            issue.setAssigneeId(dto.getAssigneeId());
            // 通知新负责人（仅当 assignee 实际变更时）
            if (!dto.getAssigneeId().equals(oldAssigneeId)) {
                notificationHelper.notifyAssigned(issue, dto.getAssigneeId(), currentUserId);
            }
        }
        if (dto.getSprintId() != null) {
            // Sprint 修改需要 sprint:edit 权限（仅 project_admin 具有）
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "sprint:edit")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "修改迭代需要 sprint:edit 权限");
            }
            String oldSprintName = null;
            if (issue.getSprintId() != null) {
                var oldSprint = sprintMapper.selectById(issue.getSprintId());
                oldSprintName = oldSprint != null ? oldSprint.getName() : null;
            }
            String newSprintName = null;
            if (dto.getSprintId() != 0) {
                var newSprint = sprintMapper.selectById(dto.getSprintId());
                newSprintName = newSprint != null ? newSprint.getName() : null;
            }
            recordActivity(id, currentUserId, "updated", "sprint", oldSprintName, newSprintName);
            issue.setSprintId(dto.getSprintId());
        }
        if (dto.getParentId() != null) {
            String oldParentKey = null;
            if (issue.getParentId() != null) {
                var oldParent = issueMapper.selectById(issue.getParentId());
                oldParentKey = oldParent != null ? oldParent.getIssueKey() : null;
            }
            String newParentKey = null;
            if (dto.getParentId() != 0) {
                // 环路检测：禁止 A→B→A 类循环引用
                validateNoCircularReference(id, dto.getParentId());
                var newParent = issueMapper.selectById(dto.getParentId());
                newParentKey = newParent != null ? newParent.getIssueKey() : null;
            }
            recordActivity(id, currentUserId, "updated", "parent", oldParentKey, newParentKey);
            issue.setParentId(dto.getParentId());
        }
        if (dto.getDueDate() != null) {
            recordActivity(id, currentUserId, "updated", "due_date",
                    issue.getDueDate() != null ? issue.getDueDate().toString() : null,
                    dto.getDueDate().toString());
            issue.setDueDate(dto.getDueDate());
        }
        if (dto.getEstimatedHours() != null) {
            recordActivity(id, currentUserId, "updated", "estimated_hours",
                    issue.getEstimatedHours() != null ? issue.getEstimatedHours() + "h" : null,
                    dto.getEstimatedHours() + "h");
            issue.setEstimatedHours(dto.getEstimatedHours());
        }
        if (dto.getCustomFields() != null) {
            try {
                issue.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (JsonProcessingException e) {
                // keep existing
            }
            // 保存自定义字段值到 EAV 表（带验证）
            Map<Long, String> fieldValues = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : dto.getCustomFields().entrySet()) {
                try {
                    fieldValues.put(Long.parseLong(entry.getKey()),
                            entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
                } catch (NumberFormatException ignored) {}
            }
            customFieldService.saveValues(issue.getId(), fieldValues,
                    issue.getIssueType(), issue.getProjectId());
        }

        issueMapper.updateById(issue);
        return issue;
    }

    /**
     * 软删除 Issue
     */
    @Transactional
    public void delete(Long id) {
        Issue issue = getById(id);
        // 归档项目不允许删除工单
        projectService.assertProjectActive(issue.getProjectId());

        // 先记录活动（deleteById 后逻辑删除字段被填充，查询会过滤掉）
        recordActivity(id, SecurityUtils.getCurrentUserId(), "deleted", null, null, null);
        // 使用 MyBatis-Plus 逻辑删除（自动设置 deleted_at = NOW()）
        issueMapper.deleteById(id);
    }

    // ========== 父子关系逻辑 ==========

    /**
     * 环路检测：沿 parent_id 链向上追溯，如果找到 issueId 则说明会形成环路。
     * 同时限制层级深度不超过 10（防止数据异常时死循环）。
     */
    private void validateNoCircularReference(Long issueId, Long newParentId) {
        if (newParentId == null || newParentId == 0) return;
        if (newParentId.equals(issueId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许将工单设为自身的子工单");
        }

        java.util.Set<Long> visited = new java.util.HashSet<>();
        visited.add(issueId);
        Long current = newParentId;
        int depth = 0;
        while (current != null && current != 0) {
            if (!visited.add(current)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许创建循环引用");
            }
            if (++depth > 10) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "父子层级过深（最多 10 级）");
            }
            Issue parent = issueMapper.selectOne(
                    new LambdaQueryWrapper<Issue>()
                            .select(Issue::getParentId)
                            .eq(Issue::getId, current)
                            .isNull(Issue::getDeletedAt)
            );
            current = parent != null ? parent.getParentId() : null;
        }
    }

    /**
     * 查询子任务列表（VO）
     */
    public List<ChildIssueVO> listChildren(Long parentId) {
        List<Map<String, Object>> rows = issueMapper.selectChildrenByParentId(parentId);
        return rows.stream().map(row -> {
            ChildIssueVO vo = new ChildIssueVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setIssueKey((String) row.get("issue_key"));
            vo.setTitle((String) row.get("title"));
            vo.setIssueType((String) row.get("issue_type"));
            vo.setPriority((String) row.get("priority"));
            vo.setStatusName((String) row.get("status_name"));
            vo.setStatusColor((String) row.get("status_color"));
            vo.setStatusCategory((String) row.get("status_category"));
            vo.setAssigneeName((String) row.get("assignee_name"));
            return vo;
        }).toList();
    }

    /**
     * 计算子任务进度汇总
     */
    public ChildProgressVO calculateChildProgress(List<ChildIssueVO> children) {
        if (children == null || children.isEmpty()) return null;

        ChildProgressVO progress = new ChildProgressVO();
        progress.setTotal(children.size());

        int closed = 0;
        for (ChildIssueVO child : children) {
            String cat = child.getStatusCategory();
            if ("done".equals(cat) || "cancelled".equals(cat)) {
                closed++;
            }
        }
        progress.setClosed(closed);
        progress.setPercent(children.isEmpty() ? 0 : Math.round((float) closed * 100 / children.size()));

        // 汇总工时：需要查询子任务的 estimated_hours 和 spent_hours
        List<Long> childIds = children.stream()
                .map(c -> Long.parseLong(c.getId()))
                .toList();
        if (!childIds.isEmpty()) {
            java.math.BigDecimal totalEstimate = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalSpent = java.math.BigDecimal.ZERO;
            List<Issue> childIssues = issueMapper.selectList(
                    new LambdaQueryWrapper<Issue>()
                            .select(Issue::getEstimatedHours, Issue::getSpentHours)
                            .in(Issue::getId, childIds)
            );
            for (Issue child : childIssues) {
                if (child.getEstimatedHours() != null) {
                    totalEstimate = totalEstimate.add(child.getEstimatedHours());
                }
                if (child.getSpentHours() != null) {
                    totalSpent = totalSpent.add(child.getSpentHours());
                }
            }
            progress.setAggregatedEstimate(totalEstimate);
            progress.setAggregatedSpent(totalSpent);
        }

        return progress;
    }

    /**
     * 检查父工单关闭时是否有未完成的子工单。
     * 返回未关闭子工单数量，0 表示无阻碍。
     */
    public long countOpenChildren(Long parentId) {
        Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();

        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getParentId, parentId)
                .isNull(Issue::getDeletedAt);
        if (!closedStatusIds.isEmpty()) {
            wrapper.notIn(Issue::getStatusId, closedStatusIds);
        }
        return issueMapper.selectCount(wrapper);
    }

    // ========== 批量操作 ==========

    /**
     * 批量操作通用执行模板。
     * <p>
     * 遍历 issueIds，对每个 issue 执行：getById → assertProjectActive → 权限校验 → action。
     * 部分成功部分失败是设计意图（非事务），结果收集在 BatchOperationResultVO 中。
     *
     * @param issueIds       工单 ID 列表
     * @param permissionCode 权限代码（如 "issue:edit"）
     * @param action         对单个 issue 执行的业务动作（入参为已通过校验的 issue），返回 null 表示成功，返回非空字符串表示业务拒绝原因
     * @param operationName  操作名称（用于日志）
     */
    private BatchOperationResultVO executeBatch(
            List<Long> issueIds,
            String permissionCode,
            BatchIssueAction action,
            String operationName) {

        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            Issue issue = null;
            try {
                issue = getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                // 使用资源级权限检查：reporter/assignee 对 issue:edit 和 issue:change_status 有额外权限
                if (!permissionService.hasIssuePermission(currentUserId, issue, permissionCode)) {
                    result.addFailure(issueId, issue.getIssueKey(), "无" + operationName + "权限");
                    continue;
                }
                String rejectReason = action.execute(issue, currentUserId);
                if (rejectReason != null) {
                    result.addFailure(issueId, issue.getIssueKey(), rejectReason);
                } else {
                    result.addSuccess();
                }
            } catch (BusinessException e) {
                String key = issue != null ? issue.getIssueKey() : "?";
                result.addFailure(issueId, key, e.getMessage());
            } catch (Exception e) {
                String key = issue != null ? issue.getIssueKey() : "?";
                result.addFailure(issueId, key, "操作失败");
                log.warn("批量{}失败 issueId={}", operationName, issueId, e);
            }
        }
        return result;
    }

    /**
     * 批量操作的单条执行动作接口
     */
    @FunctionalInterface
    private interface BatchIssueAction {
        /**
         * @return null 表示成功，非空字符串表示业务拒绝原因
         */
        String execute(Issue issue, Long currentUserId) throws Exception;
    }

    /**
     * 批量状态转换
     */
    public BatchOperationResultVO batchTransitStatus(List<Long> issueIds, Long statusId) {
        return executeBatch(issueIds, "issue:change_status", (issue, userId) -> {
            if (!workflowService.isTransitionAllowed(issue, statusId, userId)) {
                return "工作流不允许此状态转换";
            }
            transitStatus(issue.getId(), statusId, null);
            return null;
        }, "状态转换");
    }

    /**
     * 批量分配
     */
    public BatchOperationResultVO batchAssign(List<Long> issueIds, Long assigneeId) {
        return executeBatch(issueIds, "issue:assign", (issue, userId) -> {
            assign(issue.getId(), assigneeId);
            return null;
        }, "分配");
    }

    /**
     * 批量更新 Sprint
     */
    public BatchOperationResultVO batchUpdateSprint(List<Long> issueIds, Long sprintId) {
        return executeBatch(issueIds, "issue:edit", (issue, userId) -> {
            UpdateIssueDTO dto = new UpdateIssueDTO();
            dto.setSprintId(sprintId);
            update(issue.getId(), dto);
            return null;
        }, "Sprint移动");
    }

    /**
     * 批量更新优先级
     */
    public BatchOperationResultVO batchUpdatePriority(List<Long> issueIds, String priority) {
        return executeBatch(issueIds, "issue:edit", (issue, userId) -> {
            UpdateIssueDTO dto = new UpdateIssueDTO();
            dto.setPriority(priority);
            update(issue.getId(), dto);
            return null;
        }, "优先级变更");
    }

    /**
     * 批量删除
     */
    public BatchOperationResultVO batchDelete(List<Long> issueIds) {
        return executeBatch(issueIds, "issue:delete", (issue, userId) -> {
            delete(issue.getId());
            return null;
        }, "删除");
    }

    /**
     * 批量恢复（从回收站还原）
     * 注意：此方法不使用 executeBatch 模板，因为已删除工单 getById() 会抛异常
     */
    public BatchOperationResultVO batchRestore(List<Long> issueIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                Map<String, Object> row = issueMapper.selectByIdIgnoreDeleted(issueId);
                if (row == null || row.get("deleted_at") == null) {
                    result.addFailure(issueId, "?", "工单不在回收站中");
                    continue;
                }
                Long projectId = ((Number) row.get("project_id")).longValue();
                if (!permissionService.hasPermission(currentUserId, projectId, "issue:delete")) {
                    String key = row.get("issue_key") != null ? (String) row.get("issue_key") : "?";
                    result.addFailure(issueId, key, "无恢复权限");
                    continue;
                }
                issueMapper.restoreById(issueId);
                recordActivity(issueId, currentUserId, "restored", null, null, null);
                result.addSuccess();
            } catch (Exception e) {
                result.addFailure(issueId, "?", "恢复失败");
                log.warn("批量恢复失败 issueId={}", issueId, e);
            }
        }
        return result;
    }

    /**
     * 状态变更
     */
    @Transactional
    public void transitStatus(Long id, Long newStatusId, String comment) {
        transitStatus(id, newStatusId, comment, null, false);
    }

    /**
     * 状态变更（支持 manual override assignee）
     *
     * @param id                    Issue ID
     * @param newStatusId           目标状态 ID
     * @param comment               可选评论
     * @param assigneeId            显式指定的 assignee（null 表示 unassign）
     * @param assigneeExplicitlySet true = 用户明确设置了 assignee（即使为 null）
     */
    @Transactional
    public void transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet) {
        transitStatus(id, newStatusId, comment, assigneeId, assigneeExplicitlySet, null);
    }

    /**
     * 状态变更（带乐观锁版本校验）
     */
    @Transactional
    public void transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet,
                              Integer expectedVersion) {
        Issue issue = getById(id);
        // 归档项目不允许变更工单状态
        projectService.assertProjectActive(issue.getProjectId());

        // 乐观锁版本校验
        if (expectedVersion != null && !expectedVersion.equals(issue.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该工单已被其他人修改，请刷新页面后重试");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long oldStatusId = issue.getStatusId();

        // 验证目标状态存在
        IssueStatus newStatus = statusMapper.selectById(newStatusId);
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid target status");
        }

        issue.setStatusId(newStatusId);
        if (newStatus.getIsClosed()) {
            issue.setResolvedAt(LocalDateTime.now());
        } else {
            issue.setResolvedAt(null);
        }
        issueMapper.updateById(issue);

        // 记录状态变更活动
        IssueStatus oldStatus = statusMapper.selectById(oldStatusId);
        recordActivity(id, currentUserId, "status_changed", "status",
                oldStatus != null ? oldStatus.getName() : String.valueOf(oldStatusId),
                newStatus.getName());

        // 通知报告人+负责人状态已变更
        notificationHelper.notifyStatusChanged(issue, oldStatusId, newStatusId, currentUserId);

        // 如果带了评论，同时添加评论
        if (comment != null && !comment.isBlank()) {
            addComment(id, comment);
        }

        // 调用 TransitionActionEngine 执行自动化动作（auto-assign 等）
        transitionActionEngine.execute(issue, oldStatusId, newStatusId,
                currentUserId, assigneeId, assigneeExplicitlySet);
    }

    /**
     * 分配 Issue
     */
    @Transactional
    public void assign(Long id, Long assigneeId) {
        Issue issue = getById(id);
        // 归档项目不允许分配工单
        projectService.assertProjectActive(issue.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        recordActivity(id, currentUserId, "assigned", "assignee",
                issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null,
                String.valueOf(assigneeId));
        issue.setAssigneeId(assigneeId);
        issueMapper.updateById(issue);

        // 通知被分配人
        notificationHelper.notifyAssigned(issue, assigneeId, currentUserId);
    }

    // ========== 评论 ==========

    public List<IssueComment> listComments(Long issueId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<IssueComment>()
                        .eq(IssueComment::getIssueId, issueId)
                        .isNull(IssueComment::getDeletedAt)
                        .orderByAsc(IssueComment::getCreatedAt)
        );
    }

    @Transactional
    public IssueComment addComment(Long issueId, String content) {
        // 归档项目不允许添加评论
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        IssueComment comment = new IssueComment();
        comment.setIssueId(issueId);
        comment.setUserId(currentUserId);
        comment.setContent(content);
        comment.setSource("web");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        recordActivity(issueId, currentUserId, "commented", null, null, null);

        // 通知报告人+负责人+之前评论者
        notificationHelper.notifyCommented(issue, currentUserId);

        return comment;
    }

    // ========== 附件 ==========

    public List<IssueAttachment> listAttachments(Long issueId) {
        return attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId)
        );
    }

    // ========== 活动记录 ==========

    public List<IssueActivity> listActivities(Long issueId) {
        return activityMapper.selectList(
                new LambdaQueryWrapper<IssueActivity>()
                        .eq(IssueActivity::getIssueId, issueId)
                        .orderByDesc(IssueActivity::getCreatedAt)
        );
    }

    // ========== 状态 ==========

    public List<IssueStatus> listStatuses() {
        return statusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder)
        );
    }

    // ========== 增强详情（性能优化：单次 JOIN 查询） ==========

    /**
     * 获取 Issue 详情（带项目成员校验）—— 避免先查 Issue 再查详情导致两次 DB 查询
     */
    public IssueDetailVO getDetailWithAccessCheck(Long id) {
        IssueDetailVO detail = getDetail(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(currentUserId, Long.parseLong(detail.getProjectId()));
        return detail;
    }

    /**
     * 获取增强版 Issue 详情 —— 单次 SQL JOIN 替代 N+1 查询
     */
    public IssueDetailVO getDetail(Long id) {
        Map<String, Object> row = issueMapper.selectDetailById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }

        IssueDetailVO vo = new IssueDetailVO();
        vo.setId(String.valueOf(row.get("id")));
        vo.setProjectId(String.valueOf(row.get("project_id")));
        vo.setProjectName((String) row.get("project_name"));
        vo.setProjectStatus((String) row.get("project_status"));
        vo.setIssueKey((String) row.get("issue_key"));
        vo.setTitle((String) row.get("title"));
        vo.setDescription((String) row.get("description"));
        vo.setIssueType((String) row.get("issue_type"));
        vo.setStatusId(String.valueOf(row.get("status_id")));
        vo.setPriority((String) row.get("priority"));
        vo.setAssigneeId(row.get("assignee_id") != null ? String.valueOf(row.get("assignee_id")) : null);
        vo.setAssigneeName((String) row.get("assignee_name"));
        vo.setReporterId(String.valueOf(row.get("reporter_id")));
        vo.setReporterName((String) row.get("reporter_name"));
        vo.setSprintId(row.get("sprint_id") != null ? String.valueOf(row.get("sprint_id")) : null);
        vo.setSprintName((String) row.get("sprint_name"));
        vo.setParentId(row.get("parent_id") != null ? String.valueOf(row.get("parent_id")) : null);
        vo.setParentKey((String) row.get("parent_key"));
        vo.setCustomFields((String) row.get("custom_fields"));

        if (row.get("due_date") != null) {
            vo.setDueDate(((java.sql.Date) row.get("due_date")).toLocalDate());
        }
        if (row.get("estimated_hours") != null) {
            vo.setEstimatedHours((java.math.BigDecimal) row.get("estimated_hours"));
        }
        if (row.get("spent_hours") != null) {
            vo.setSpentHours((java.math.BigDecimal) row.get("spent_hours"));
        }

        if (row.get("resolved_at") != null) {
            vo.setResolvedAt(((java.sql.Timestamp) row.get("resolved_at")).toLocalDateTime());
        }
        if (row.get("created_at") != null) {
            vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
        }
        if (row.get("updated_at") != null) {
            vo.setUpdatedAt(((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime());
        }

        // 乐观锁版本号
        if (row.get("version") != null) {
            vo.setVersion(((Number) row.get("version")).intValue());
        }

        // 状态对象
        if (row.get("status_name") != null) {
            IssueStatusVO statusVO = new IssueStatusVO();
            statusVO.setId(String.valueOf(row.get("status_id")));
            statusVO.setName((String) row.get("status_name"));
            statusVO.setCode((String) row.get("status_code"));
            statusVO.setColor((String) row.get("status_color"));
            statusVO.setCategory((String) row.get("status_category"));
            statusVO.setIsDefault((Boolean) row.get("status_is_default"));
            statusVO.setIsClosed((Boolean) row.get("status_is_closed"));
            vo.setStatus(statusVO);
        }

        // 标签（单独查询，因为是多对多关系）
        List<IssueTag> tags = tagService.listIssueTags(id);
        vo.setTags(issueConverter.toTagVOList(tags));

        // 自定义字段结构化值
        Long projectIdLong = Long.parseLong(vo.getProjectId());
        vo.setCustomFieldDetails(customFieldService.getValuesForDisplay(id, projectIdLong, vo.getIssueType()));

        // 子任务列表 + 进度汇总
        List<ChildIssueVO> children = listChildren(id);
        if (!children.isEmpty()) {
            vo.setChildren(children);
            vo.setChildProgress(calculateChildProgress(children));
        }

        return vo;
    }

    /**
     * 获取评论列表 —— 单次 JOIN 查询（消除 N+1）
     */
    public List<IssueCommentVO> listCommentsWithUser(Long issueId) {
        List<Map<String, Object>> rows = issueMapper.selectCommentsWithUser(issueId);
        return rows.stream().map(row -> {
            IssueCommentVO vo = new IssueCommentVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setIssueId(String.valueOf(row.get("issue_id")));
            vo.setUserId(String.valueOf(row.get("user_id")));
            vo.setUserName((String) row.get("user_name"));
            vo.setUserAvatar((String) row.get("user_avatar"));
            vo.setContent((String) row.get("content"));
            vo.setSource((String) row.get("source"));
            if (row.get("created_at") != null) vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
            if (row.get("updated_at") != null) vo.setUpdatedAt(((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime());
            return vo;
        }).toList();
    }

    /**
     * 获取活动列表 —— 单次 JOIN 查询（消除 N+1）
     * assignee 字段的 old/new value 在 SQL 层自动解析为用户显示名
     */
    public List<IssueActivityVO> listActivitiesWithUser(Long issueId) {
        List<Map<String, Object>> rows = issueMapper.selectActivitiesWithUser(issueId);
        return rows.stream().map(row -> {
            IssueActivityVO vo = new IssueActivityVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setIssueId(String.valueOf(row.get("issue_id")));
            vo.setUserId(String.valueOf(row.get("user_id")));
            vo.setUserName((String) row.get("user_name"));
            vo.setAction((String) row.get("action"));
            vo.setFieldName((String) row.get("field_name"));
            vo.setOldValue((String) row.get("old_value"));
            vo.setNewValue((String) row.get("new_value"));
            if (row.get("created_at") != null) vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
            return vo;
        }).toList();
    }

    // ========== 附件上传 ==========

    /**
     * 上传附件
     */
    @Transactional
    public IssueAttachment uploadAttachment(Long issueId, MultipartFile file) {
        // 验证 Issue 存在
        Issue issue = getById(issueId);
        // 归档项目不允许上传附件
        projectService.assertProjectActive(issue.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 上传到 MinIO
        String folder = "issues/" + issueId + "/attachments";
        String filePath = minioService.upload(folder, file.getOriginalFilename(), file);

        // 保存 DB 记录
        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssueId(issueId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFilePath(filePath);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setUploadedBy(currentUserId);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);

        // 记录活动
        recordActivity(issueId, currentUserId, "attachment_added", "attachment", null, file.getOriginalFilename());

        return attachment;
    }

    /**
     * 删除附件
     */
    @Transactional
    public void deleteAttachment(Long issueId, Long attachmentId) {
        // 归档项目不允许删除附件
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }

        // 从 MinIO 删除
        minioService.delete(attachment.getFilePath());
        // 从 DB 删除
        attachmentMapper.deleteById(attachmentId);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        recordActivity(issueId, currentUserId, "attachment_removed", "attachment", attachment.getFileName(), null);
    }

    // ========== 回收站 ==========

    /**
     * 回收站列表：查询指定项目中已删除的 Issue（分页）
     */
    public PageResult<IssueTrashVO> listTrash(Long projectId, int page, int pageSize) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(currentUserId, projectId);
        Page<Map<String, Object>> p = new Page<>(page, pageSize);
        Page<Map<String, Object>> result = issueMapper.selectTrashPage(p, projectId);

        List<IssueTrashVO> voList = result.getRecords().stream().map(row -> {
            IssueTrashVO vo = new IssueTrashVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setProjectId(String.valueOf(row.get("project_id")));
            vo.setIssueKey((String) row.get("issue_key"));
            vo.setTitle((String) row.get("title"));
            vo.setIssueType((String) row.get("issue_type"));
            vo.setPriority((String) row.get("priority"));
            vo.setAssigneeName((String) row.get("assignee_name"));
            vo.setDeletedByName((String) row.get("deleted_by_name"));
            Object deletedAt = row.get("deleted_at");
            if (deletedAt instanceof java.sql.Timestamp ts) {
                vo.setDeletedAt(ts.toLocalDateTime());
            } else if (deletedAt instanceof LocalDateTime ldt) {
                vo.setDeletedAt(ldt);
            }
            return vo;
        }).toList();

        return new PageResult<>(voList, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 恢复已删除的 Issue（从回收站还原）
     */
    @Transactional
    public void restore(Long id) {
        Map<String, Object> row = issueMapper.selectByIdIgnoreDeleted(id);
        if (row == null || row.get("deleted_at") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单不在回收站中");
        }
        Long projectId = ((Number) row.get("project_id")).longValue();
        projectService.assertProjectActive(projectId);

        int affected = issueMapper.restoreById(id);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "恢复失败，工单不在回收站中");
        }
        recordActivity(id, SecurityUtils.getCurrentUserId(), "restored", null, null, null);
    }

    /**
     * 永久删除 Issue（物理删除），同时清理关联数据
     */
    @Transactional
    public void permanentDelete(Long id) {
        Map<String, Object> row = issueMapper.selectByIdIgnoreDeleted(id);
        if (row == null || row.get("deleted_at") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能永久删除回收站中的工单");
        }

        // 清理附件（MinIO + DB）
        List<IssueAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, id)
        );
        for (IssueAttachment att : attachments) {
            minioService.delete(att.getFilePath());
        }
        attachmentMapper.delete(new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, id));

        // 清理评论
        commentMapper.delete(new LambdaQueryWrapper<IssueComment>().eq(IssueComment::getIssueId, id));

        // 清理活动记录
        activityMapper.delete(new LambdaQueryWrapper<IssueActivity>().eq(IssueActivity::getIssueId, id));

        // 物理删除工单
        issueMapper.permanentDeleteById(id);
    }

    /**
     * 获取已删除工单所属的 projectId（用于 @PreAuthorize SpEL）
     */
    public Long getDeletedIssueProjectId(Long issueId) {
        Map<String, Object> row = issueMapper.selectByIdIgnoreDeleted(issueId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return ((Number) row.get("project_id")).longValue();
    }

    // ========== 内部方法 ==========

    private void recordActivity(Long issueId, Long userId, String action,
                                String fieldName, String oldValue, String newValue) {
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }
}

package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.service.MinioService;
import com.trackflow.common.util.SecurityUtils;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueActivityMapper activityMapper;
    private final com.trackflow.timeentry.mapper.TimeEntryMapper timeEntryMapper;
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

        issue.setDeletedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        recordActivity(id, SecurityUtils.getCurrentUserId(), "deleted", null, null, null);
    }

    // ========== 批量操作 ==========

    /**
     * 批量状态转换
     */
    public BatchOperationResultVO batchTransitStatus(List<Long> issueIds, Long statusId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                Issue issue = getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:change_status")) {
                    result.addFailure(issueId, issue.getIssueKey(), "无状态变更权限");
                    continue;
                }
                if (!workflowService.isTransitionAllowed(issue, statusId, currentUserId)) {
                    result.addFailure(issueId, issue.getIssueKey(), "工作流不允许此状态转换");
                    continue;
                }
                transitStatus(issueId, statusId, null);
                result.addSuccess();
            } catch (BusinessException e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", e.getMessage());
            } catch (Exception e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", "操作失败");
                log.warn("批量状态转换失败 issueId={}", issueId, e);
            }
        }
        return result;
    }

    /**
     * 批量分配
     */
    public BatchOperationResultVO batchAssign(List<Long> issueIds, Long assigneeId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                Issue issue = getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:assign")) {
                    result.addFailure(issueId, issue.getIssueKey(), "无分配权限");
                    continue;
                }
                assign(issueId, assigneeId);
                result.addSuccess();
            } catch (BusinessException e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", e.getMessage());
            } catch (Exception e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", "操作失败");
                log.warn("批量分配失败 issueId={}", issueId, e);
            }
        }
        return result;
    }

    /**
     * 批量更新 Sprint
     */
    public BatchOperationResultVO batchUpdateSprint(List<Long> issueIds, Long sprintId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                Issue issue = getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:edit")) {
                    result.addFailure(issueId, issue.getIssueKey(), "无编辑权限");
                    continue;
                }
                UpdateIssueDTO dto = new UpdateIssueDTO();
                dto.setSprintId(sprintId);
                update(issueId, dto);
                result.addSuccess();
            } catch (BusinessException e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", e.getMessage());
            } catch (Exception e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", "操作失败");
                log.warn("批量更新Sprint失败 issueId={}", issueId, e);
            }
        }
        return result;
    }

    /**
     * 批量更新优先级
     */
    public BatchOperationResultVO batchUpdatePriority(List<Long> issueIds, String priority) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                Issue issue = getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:edit")) {
                    result.addFailure(issueId, issue.getIssueKey(), "无编辑权限");
                    continue;
                }
                UpdateIssueDTO dto = new UpdateIssueDTO();
                dto.setPriority(priority);
                update(issueId, dto);
                result.addSuccess();
            } catch (BusinessException e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", e.getMessage());
            } catch (Exception e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", "操作失败");
                log.warn("批量更新优先级失败 issueId={}", issueId, e);
            }
        }
        return result;
    }

    /**
     * 批量删除
     */
    public BatchOperationResultVO batchDelete(List<Long> issueIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResultVO result = new BatchOperationResultVO();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                Issue issue = getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:delete")) {
                    result.addFailure(issueId, issue.getIssueKey(), "无删除权限");
                    continue;
                }
                delete(issueId);
                result.addSuccess();
            } catch (BusinessException e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", e.getMessage());
            } catch (Exception e) {
                Issue issue = issueMapper.selectById(issueId);
                result.addFailure(issueId, issue != null ? issue.getIssueKey() : "?", "操作失败");
                log.warn("批量删除失败 issueId={}", issueId, e);
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
        Issue issue = getById(id);
        // 归档项目不允许变更工单状态
        projectService.assertProjectActive(issue.getProjectId());

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

        // Calculate actual spent time from time_entry table
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.trackflow.timeentry.entity.TimeEntry> teWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        teWrapper.eq("issue_id", id).select("duration");
        List<com.trackflow.timeentry.entity.TimeEntry> timeEntries = timeEntryMapper.selectList(teWrapper);
        if (!timeEntries.isEmpty()) {
            int totalMinutes = timeEntries.stream().mapToInt(com.trackflow.timeentry.entity.TimeEntry::getDuration).sum();
            // Convert minutes to hours as BigDecimal
            vo.setSpentHours(java.math.BigDecimal.valueOf(totalMinutes).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
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

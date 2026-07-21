package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.config.AttachmentConfig;
import com.trackflow.common.context.NotificationContext;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.service.MinioService;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.SqlUtils;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.service.CustomFieldSortHelper;
import com.trackflow.customfield.service.CustomFieldValidateMode;
import com.trackflow.issue.dto.CreateIssueDTO;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.dto.MoveIssueDTO;
import com.trackflow.issue.dto.UpdateIssueDTO;
import com.trackflow.issue.entity.*;
import com.trackflow.issue.mapper.*;
import com.trackflow.issue.mapper.result.*;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupMemberMapper;
import com.trackflow.system.mapper.UserGroupMapper;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.vo.*;
import com.trackflow.workflow.service.TransitionActionEngine;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.ActionExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.common.event.WorkflowRuleEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    /**
     * Update 操作结果，包含更新后的实体和附加标志
     */
    public record UpdateResult(Issue issue, boolean statusAutoReset) {}

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueKeyHistoryMapper issueKeyHistoryMapper;
    private final com.trackflow.sprint.mapper.SprintMapper sprintMapper;
    private final ProjectService projectService;
    private final MinioService minioService;
    private final IssueConverter issueConverter;
    private final IssueTagService tagService;
    private final PermissionService permissionService;
    private final TransitionActionEngine transitionActionEngine;
    private final WorkflowService workflowService;
    private final StatusCacheHelper statusCacheHelper;
    private final CustomFieldService customFieldService;
    private final CustomFieldSortHelper customFieldSortHelper;
    private final SysUserMapper sysUserMapper;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final UserGroupMapper userGroupMapper;
    private final AttachmentConfig attachmentConfig;
    private final AncestorRefreshService ancestorRefreshService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.trackflow.timeentry.mapper.TimeEntryMapper timeEntryMapper;
    private final com.trackflow.integration.service.MutedThreadService mutedThreadService;
    private final com.trackflow.board.mapper.BoardColumnConfigMapper boardColumnConfigMapper;
    private final com.trackflow.issue.service.precheck.ClosePreCheckChain closePreCheckChain;

    /**
     * 创建 Issue
     */
    @Transactional(rollbackFor = Exception.class)
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
        // 校验 assignee 是否为有效的项目成员
        validateAssignee(dto.getAssigneeId(), dto.getProjectId());
        issue.setAssigneeId(normalizeAssigneeId(dto.getAssigneeId()));
        issue.setReporterId(currentUserId);
        // 如果未指定 sprintId，尝试使用项目默认 Sprint
        Long resolvedSprintId = dto.getSprintId();
        if (resolvedSprintId == null) {
            Long defaultSprintId = projectService.getProjectSettingAsLong(dto.getProjectId(), "defaultSprintId");
            if (defaultSprintId != null) {
                // 验证默认 Sprint 仍然有效（存在且未完成）
                var defaultSprint = sprintMapper.selectById(defaultSprintId);
                if (defaultSprint != null
                        && defaultSprint.getProjectId().equals(dto.getProjectId())
                        && defaultSprint.getStatus() != com.trackflow.sprint.entity.SprintStatus.COMPLETED) {
                    resolvedSprintId = defaultSprintId;
                }
            }
        }
        issue.setSprintId(resolvedSprintId);
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

        issueMapper.insert(issue);

        // 自动分配：用户未指定 assignee 时，触发创建时自动分配规则
        if (issue.getAssigneeId() == null) {
            transitionActionEngine.executeOnCreate(issue, currentUserId);
        }

        // 保存自定义字段值到 EAV 表（带默认值应用 + 必填校验）
        Map<Long, String> userFieldValues = new java.util.HashMap<>();
        if (dto.getCustomFields() != null) {
            for (Map.Entry<String, String> entry : dto.getCustomFields().entrySet()) {
                Long fieldId = parseFieldId(entry.getKey());
                userFieldValues.put(fieldId, entry.getValue());
            }
        }
        // 应用默认值并校验必填字段（始终执行，无论用户是否传了 customFields）
        Map<Long, String> mergedFieldValues = customFieldService.applyDefaultsAndValidate(
                userFieldValues, issue.getIssueType(), issue.getProjectId());
        if (!mergedFieldValues.isEmpty()) {
            customFieldService.saveValues(issue.getId(), mergedFieldValues,
                    issue.getIssueType(), issue.getProjectId());
        }

        // 记录活动
        recordActivity(issue.getId(), currentUserId, "created", null, null, null);

        // 通知被分配人（若创建时指定了 assignee）— 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Created(issue, currentUserId));

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "issue_created"));

        // 如果指定了父工单，刷新祖先链的派生属性
        if (issue.getParentId() != null && issue.getParentId() != 0) {
            ancestorRefreshService.refreshAncestorChain(issue.getParentId());
        }

        // 触发 on-create 自动化规则（事务提交后异步执行，通过事件解耦）
        eventPublisher.publishEvent(new WorkflowRuleEvent.IssueCreated(issue.getId(), issue.getProjectId()));

        return issue;
    }

    /**
     * Issue 列表（接受 IssueQuery，完整筛选支持）
     * 强制按用户所属项目过滤：如果指定了 projectId，校验成员关系；如果未指定，自动限定为所属项目。
     * <p>
     * 注：此方法使用 QueryWrapper（非 LambdaQueryWrapper），因为：
     * 1. applyFilter/applyNegativeFilter 辅助方法通过动态列名参数化实现通用筛选
     * 2. applyKeywordFilter 和 excludeDoneBefore 使用 .apply() 子查询语法，需要 raw SQL
     * 这些场景 LambdaQueryWrapper 无法覆盖，故保留 QueryWrapper。
     */
    public Page<Issue> listByQuery(IssueQuery query) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (query.getProjectId() != null) {
            // 指定了 projectId，校验可访问性（兼容 internal/public 项目的非成员访问）
            projectService.assertProjectAccessible(currentUserId, query.getProjectId());
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
        // assigneeName: lookup by display name (used by report drill-down)
        if (query.getAssigneeName() != null && !query.getAssigneeName().isBlank()) {
            wrapper.apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name = {0})", query.getAssigneeName().trim());
        }
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

        // hideResolved: exclude all is_closed=true statuses
        if ("true".equals(query.getHideResolved())) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.notIn("status_id", closedStatusIds);
            }
        }

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

        // excludeDoneBefore: 排除在此日期之前完成的工单（看板"已完成保留天数"服务端过滤）
        // 逻辑：status 不属于 done/cancelled → 保留；属于 done/cancelled → resolved_at >= cutoff 或 resolved_at IS NULL 时保留
        if (query.getExcludeDoneBefore() != null) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                // NOT (status_id IN (closed) AND resolved_at < cutoff)
                // 等价于：status NOT closed OR resolved_at >= cutoff OR resolved_at IS NULL
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

        // 处理排序：自定义字段排序通过子查询实现，内置字段通过 toPage() 处理
        String sort = query.getSort();
        boolean hasCustomFieldSort = false;
        if (sort != null && !sort.isBlank()) {
            // 解析排序方向（-fieldName 降序，fieldName 升序）
            boolean desc = sort.startsWith("-");
            String sortField = desc ? sort.substring(1) : sort;
            if (customFieldSortHelper.isCustomFieldSortKey(sortField)) {
                hasCustomFieldSort = customFieldSortHelper.applyCustomFieldSort(wrapper, sortField, !desc);
            }
        }

        // 默认排序兜底（当无有效自定义字段排序且 toPage() 也无有效排序时生效）
        if (!hasCustomFieldSort) {
            wrapper.orderByDesc("updated_at");
        }

        // 对于自定义字段排序，清空 sort 参数避免 toPage() 产生冲突的 ORDER BY
        if (hasCustomFieldSort) {
            String originalSort = query.getSort();
            query.setSort(null);
            Page<Issue> result = issueMapper.selectPage(query.toPage(), wrapper);
            query.setSort(originalSort); // 恢复原值，避免影响调用方
            return result;
        }
        return issueMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 工单列表查询（带关联数据填充）。
     * 在 listByQuery 基础上批量填充用户名/头像、状态名/颜色、Sprint名、子任务进度、自定义字段值。
     */
    public PageResult<IssueVO> listWithDetails(IssueQuery query) {
        Page<Issue> result = listByQuery(query);
        List<IssueVO> voList = issueConverter.toVOList(result.getRecords());

        fillUserInfo(result.getRecords(), voList);
        fillChildProgress(result.getRecords(), voList);
        fillStatusInfo(result.getRecords(), voList);
        fillSprintInfo(result.getRecords(), voList);
        fillCustomFieldValues(result.getRecords(), voList);

        return new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 批量填充 assigneeName/assigneeAvatarUrl/reporterName
     */
    private void fillUserInfo(List<Issue> issues, List<IssueVO> voList) {
        Set<Long> userIds = new java.util.HashSet<>();
        for (Issue issue : issues) {
            if (issue.getAssigneeId() != null) userIds.add(issue.getAssigneeId());
            if (issue.getReporterId() != null) userIds.add(issue.getReporterId());
        }
        if (userIds.isEmpty()) return;

        Map<Long, com.trackflow.system.entity.SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.trackflow.system.entity.SysUser::getId, u -> u, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getAssigneeId() != null) {
                com.trackflow.system.entity.SysUser user = userMap.get(issue.getAssigneeId());
                if (user != null) {
                    voList.get(i).setAssigneeName(user.getDisplayName());
                    voList.get(i).setAssigneeAvatarUrl(user.getAvatarUrl());
                }
            }
            if (issue.getReporterId() != null) {
                com.trackflow.system.entity.SysUser user = userMap.get(issue.getReporterId());
                if (user != null) {
                    voList.get(i).setReporterName(user.getDisplayName());
                }
            }
        }
    }

    /**
     * 填充子任务进度字段（childCount / childClosedCount）
     */
    private void fillChildProgress(List<Issue> issues, List<IssueVO> voList) {
        for (int i = 0; i < issues.size(); i++) {
            voList.get(i).setChildCount(issues.get(i).getChildCount());
            voList.get(i).setChildClosedCount(issues.get(i).getChildClosedCount());
        }
    }

    /**
     * 批量填充 statusName/statusColor（status 表数据极少，全量缓存查出）
     */
    private void fillStatusInfo(List<Issue> issues, List<IssueVO> voList) {
        Map<Long, IssueStatus> statusMap = statusMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getStatusId() != null) {
                IssueStatus status = statusMap.get(issue.getStatusId());
                if (status != null) {
                    voList.get(i).setStatusName(status.getName());
                    voList.get(i).setStatusColor(status.getColor());
                }
            }
        }
    }

    /**
     * 批量填充 sprintName（仅查询用到的 Sprint）
     */
    private void fillSprintInfo(List<Issue> issues, List<IssueVO> voList) {
        Set<Long> sprintIds = new java.util.HashSet<>();
        for (Issue issue : issues) {
            if (issue.getSprintId() != null) sprintIds.add(issue.getSprintId());
        }
        if (sprintIds.isEmpty()) return;

        Map<Long, com.trackflow.sprint.entity.Sprint> sprintMap = sprintMapper.selectBatchIds(sprintIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.trackflow.sprint.entity.Sprint::getId, s -> s, (a, b) -> a));

        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (issue.getSprintId() != null) {
                com.trackflow.sprint.entity.Sprint sprint = sprintMap.get(issue.getSprintId());
                if (sprint != null) {
                    voList.get(i).setSprintName(sprint.getName());
                }
            }
        }
    }

    /**
     * 批量填充自定义字段展示值和颜色
     */
    private void fillCustomFieldValues(List<Issue> issues, List<IssueVO> voList) {
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        if (issueIds.isEmpty()) return;

        Map<Long, Map<String, String>> cfColorsMap = new java.util.HashMap<>();
        Map<Long, Map<String, String>> cfValuesMap = customFieldService.getBatchDisplayValues(issueIds, cfColorsMap);

        for (int i = 0; i < issues.size(); i++) {
            Long issueId = issues.get(i).getId();
            Map<String, String> cfValues = cfValuesMap.get(issueId);
            if (cfValues != null && !cfValues.isEmpty()) {
                voList.get(i).setCustomFieldValues(cfValues);
            }
            Map<String, String> cfColors = cfColorsMap.get(issueId);
            if (cfColors != null && !cfColors.isEmpty()) {
                voList.get(i).setCustomFieldColors(cfColors);
            }
        }
    }

    /**
     * WIP 限制校验：检查单个工单的状态转换是否会超出目标列 WIP 上限。
     *
     * @return 超限警告信息；null 表示不超限或目标列无 WIP 配置
     */
    public String checkWipLimit(Long projectId, Long targetStatusId) {
        com.trackflow.board.entity.BoardColumnConfig columnConfig = boardColumnConfigMapper.selectOne(
                new LambdaQueryWrapper<com.trackflow.board.entity.BoardColumnConfig>()
                        .eq(com.trackflow.board.entity.BoardColumnConfig::getProjectId, projectId)
                        .eq(com.trackflow.board.entity.BoardColumnConfig::getStatusId, targetStatusId));
        if (columnConfig == null || columnConfig.getWipMax() == null) return null;

        long currentCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getStatusId, targetStatusId));
        if (currentCount >= columnConfig.getWipMax()) {
            return String.format("目标列已达到 WIP 上限（%d/%d），确定要继续移入吗？",
                    currentCount, columnConfig.getWipMax());
        }
        return null;
    }

    /**
     * 批量状态转换的 WIP 限制预检查。
     * 按项目分组统计：移入后是否会超出目标列 WIP 上限。
     *
     * @return 超限警告信息；null 表示不超限或目标列无 WIP 配置
     */
    public String checkBatchWipLimit(List<Long> issueIds, Long targetStatusId) {
        List<Issue> issues = issueMapper.selectBatchIds(issueIds);
        if (issues.isEmpty()) return null;

        // 按项目分组，只统计会真正移入目标状态的（排除已经在目标状态的）
        Map<Long, Long> projectMoveInCount = issues.stream()
                .filter(i -> !targetStatusId.equals(i.getStatusId()))
                .collect(java.util.stream.Collectors.groupingBy(Issue::getProjectId, java.util.stream.Collectors.counting()));

        if (projectMoveInCount.isEmpty()) return null;

        List<String> warnings = new java.util.ArrayList<>();
        for (Map.Entry<Long, Long> entry : projectMoveInCount.entrySet()) {
            Long projectId = entry.getKey();
            long moveInCount = entry.getValue();

            com.trackflow.board.entity.BoardColumnConfig columnConfig = boardColumnConfigMapper.selectOne(
                    new LambdaQueryWrapper<com.trackflow.board.entity.BoardColumnConfig>()
                            .eq(com.trackflow.board.entity.BoardColumnConfig::getProjectId, projectId)
                            .eq(com.trackflow.board.entity.BoardColumnConfig::getStatusId, targetStatusId));
            if (columnConfig == null || columnConfig.getWipMax() == null) continue;

            long currentCount = issueMapper.selectCount(
                    new LambdaQueryWrapper<Issue>()
                            .eq(Issue::getProjectId, projectId)
                            .eq(Issue::getStatusId, targetStatusId));
            long afterCount = currentCount + moveInCount;
            if (afterCount > columnConfig.getWipMax()) {
                warnings.add(String.format("项目中目标列将达到 %d/%d", afterCount, columnConfig.getWipMax()));
            }
        }

        if (warnings.isEmpty()) return null;
        return "批量操作将超出 WIP 上限（" + String.join("；", warnings) + "），确定要继续吗？";
    }

    /**
     * 状态转换前置校验（WIP 限制 + 关闭前置检查）。
     * Controller 调用此方法获取警告信息，由 Controller 决定返回给前端让用户确认。
     *
     * @return 警告信息列表（空表示无需确认，可直接转换）
     */
    public record TransitPreCheckResult(String wipWarning, String closeWarning) {
        public boolean hasWarnings() {
            return wipWarning != null || closeWarning != null;
        }
    }

    public TransitPreCheckResult checkTransitPreConditions(Issue issue, Long targetStatusId, boolean forceWip, boolean forceClose) {
        // WIP 限制校验
        String wipWarning = null;
        if (!forceWip) {
            wipWarning = checkWipLimit(issue.getProjectId(), targetStatusId);
        }

        // 关闭状态前置检查
        String closeWarning = null;
        if (!forceClose) {
            IssueStatus targetStatus = statusMapper.selectById(targetStatusId);
            if (targetStatus != null && targetStatus.getIsClosed()) {
                List<String> warnings = closePreCheckChain.execute(issue);
                if (!warnings.isEmpty()) {
                    closeWarning = "此工单" + String.join("，且", warnings) + "，确定要强制关闭吗？";
                }
            }
        }

        return new TransitPreCheckResult(wipWarning, closeWarning);
    }

    /**
     * 撤销状态转换时验证目标状态是否为上次变更的旧状态。
     *
     * @throws BusinessException 如果目标状态不存在或不匹配
     */
    public void validateUndoTargetStatus(Long targetStatusId, IssueActivity lastStatusChange) {
        IssueStatus targetStatus = statusMapper.selectById(targetStatusId);
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标状态不存在");
        }
        String oldValue = lastStatusChange.getOldValue();
        if (!targetStatus.getName().equals(oldValue) && !targetStatus.getLocalizedName().equals(oldValue)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "撤销操作只能回退到上一个状态（" + lastStatusChange.getOldValue() + "），不允许任意跳转");
        }
    }

    /**
     * 通用筛选条件应用（支持逗号分隔多值和 "none" 关键字）。
     * 使用字符串列名以实现多字段复用，避免为每个字段写重复代码。
     */
    private void applyFilter(QueryWrapper<Issue> wrapper, String column, String value, boolean isNumeric) {
        if (value == null || value.isBlank()) return;
        // "none" means IS NULL (e.g. sprintId=none → issues with no sprint)
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
        List<?> values = isNumeric
                ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        wrapper.notIn(column, values);
    }

    /**
     * 关键词过滤：匹配 title、description、issue_key 或 assignee 的 display_name/username。
     * 使用参数化查询防止 SQL 注入。LIKE 通配符已转义以确保字面匹配。
     */
    private void applyKeywordFilter(QueryWrapper<Issue> wrapper, String keyword) {
        String escaped = SqlUtils.escapeLikePattern(keyword);
        String likePattern = "%" + escaped + "%";
        wrapper.and(w -> w
                .apply("title LIKE {0} ESCAPE '\\'", likePattern)
                .or()
                .apply("description LIKE {0} ESCAPE '\\'", likePattern)
                .or()
                .apply("issue_key LIKE {0} ESCAPE '\\'", likePattern)
                .or()
                .apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name LIKE {0} ESCAPE '\\' OR username LIKE {0} ESCAPE '\\')", likePattern)
        );
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
        projectService.assertProjectAccessible(currentUserId, issue.getProjectId());
        return issue;
    }

    /**
     * 通过 issueKey 获取（带项目成员校验）
     */
    public Issue getByKeyWithAccessCheck(String issueKey) {
        Issue issue = getByKey(issueKey);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(currentUserId, issue.getProjectId());
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
            // 回退查询：通过历史 Key 查找工单
            IssueKeyHistory keyHistory = issueKeyHistoryMapper.selectOne(
                    new LambdaQueryWrapper<IssueKeyHistory>().eq(IssueKeyHistory::getOldKey, issueKey)
            );
            if (keyHistory != null) {
                issue = issueMapper.selectOne(
                        new LambdaQueryWrapper<Issue>().eq(Issue::getId, keyHistory.getIssueId()).isNull(Issue::getDeletedAt)
                );
            }
        }
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return issue;
    }

    /**
     * 更新 Issue
     */
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult update(Long id, UpdateIssueDTO dto) {
        Issue issue = getById(id);
        // 归档项目不允许编辑工单
        projectService.assertProjectActive(issue.getProjectId());

        // 乐观锁版本校验：前端携带 version 时检查是否与 DB 一致
        if (dto.getVersion() != null && !dto.getVersion().equals(issue.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该工单已被其他人修改，请刷新页面后重试");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean statusAutoReset = false;

        // 收集旧值快照——在字段赋值之前记录，供规则引擎事件使用
        Map<String, String> oldValues = new java.util.HashMap<>();
        if (dto.getTitle() != null) {
            oldValues.put("title", issue.getTitle());
        }
        if (dto.getIssueType() != null) {
            oldValues.put("issue_type", issue.getIssueType());
        }
        if (dto.getPriority() != null) {
            oldValues.put("priority", issue.getPriority());
        }
        if (dto.getAssigneeId() != null) {
            oldValues.put("assignee", issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : null);
        }
        if (dto.getSprintId() != null) {
            oldValues.put("sprint", issue.getSprintId() != null ? String.valueOf(issue.getSprintId()) : null);
        }
        if (dto.getDueDate() != null) {
            oldValues.put("due_date", issue.getDueDate() != null ? issue.getDueDate().toString() : null);
        }

        // 收集通知用字段变更：fieldName → [oldValue, newValue]
        // 所有通知在方法末尾统一发布一个 MultiFieldUpdated 事件，避免聚合覆盖丢失信息
        Map<String, String[]> fieldChanges = new java.util.LinkedHashMap<>();

        if (dto.getTitle() != null) {
            String trimmedTitle = dto.getTitle().trim();
            if (trimmedTitle.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能为空");
            }
            recordActivity(id, currentUserId, "updated", "title", issue.getTitle(), trimmedTitle);
            issue.setTitle(trimmedTitle);
        }
        if (dto.getDescription() != null) {
            recordActivity(id, currentUserId, "updated", "description",
                    issue.getDescription() != null ? "（已有内容）" : null,
                    dto.getDescription() != null ? "（已更新）" : null);
            issue.setDescription(dto.getDescription());
            // 收集描述变更（不传具体内容，仅告知有变更）
            fieldChanges.put("description", new String[]{null, null});
        }
        if (dto.getIssueType() != null && !dto.getIssueType().equals(issue.getIssueType())) {
            String oldType = issue.getIssueType();
            String newType = dto.getIssueType();

            // 1. 工作流状态兼容性检查：当前状态在新类型的工作流图中是否仍然可达
            boolean statusValid = workflowService.isStatusInWorkflow(
                    issue.getProjectId(), newType, issue.getStatusId());
            if (!statusValid) {
                // 自动回退到系统默认状态
                IssueStatus defaultStatus = workflowService.getDefaultStatus();
                if (defaultStatus != null) {
                    Long oldStatusId = issue.getStatusId();
                    issue.setStatusId(defaultStatus.getId());
                    String oldStatusName = statusCacheHelper.getStatusName(oldStatusId);
                    String newStatusName = defaultStatus.getLocalizedName();
                    recordActivity(id, currentUserId, "status_reset", "status",
                            oldStatusName, newStatusName);
                    statusAutoReset = true;
                    log.info("Issue {} type changed from {} to {}: status auto-reset from {} to default ({})",
                            id, oldType, newType, oldStatusName, newStatusName);
                }
            }

            // 2. 自定义字段重新适配：删除不再适用于新类型的字段值
            customFieldService.removeOrphanValues(issue.getId(), newType, issue.getProjectId());

            recordActivity(id, currentUserId, "updated", "issue_type", oldType, newType);
            issue.setIssueType(newType);
        }
        if (dto.getPriority() != null) {
            String oldPriority = issue.getPriority();
            recordActivity(id, currentUserId, "updated", "priority", oldPriority, dto.getPriority());
            issue.setPriority(dto.getPriority());
            // 收集优先级变更（仅当实际变更时）
            if (!dto.getPriority().equals(oldPriority)) {
                fieldChanges.put("priority", new String[]{oldPriority, dto.getPriority()});
            }
        }
        if (dto.getAssigneeId() != null) {
            // 校验 assignee 是否为有效的项目成员（assigneeId=0 表示取消分配，跳过校验）
            validateAssignee(dto.getAssigneeId(), issue.getProjectId());
            Long normalizedAssigneeId = normalizeAssigneeId(dto.getAssigneeId());
            // 存储用户显示名快照（而非 userId），确保历史记录不可变
            String oldAssigneeName = getUserDisplayName(issue.getAssigneeId());
            String newAssigneeName = getUserDisplayName(normalizedAssigneeId);
            recordActivity(id, currentUserId, "assigned", "assignee", oldAssigneeName, newAssigneeName);
            Long oldAssigneeId = issue.getAssigneeId();
            issue.setAssigneeId(normalizedAssigneeId);
            // 通知新负责人（仅当 assignee 实际变更且不为空时）— 事务提交后触发
            // Assigned 事件保持独立发布（有专门的通知逻辑，不走 FieldUpdated 路径）
            if (normalizedAssigneeId != null && !normalizedAssigneeId.equals(oldAssigneeId)) {
                eventPublisher.publishEvent(new IssueNotificationEvent.Assigned(issue, normalizedAssigneeId, currentUserId));
            }
        }
        if (dto.getSprintId() != null) {
            // Sprint 修改需要 sprint:edit 权限（仅 project_admin 具有）
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "sprint:edit")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "修改迭代需要 sprint:edit 权限");
            }
            String oldSprintId = null;
            String oldSprintName = null;
            if (issue.getSprintId() != null) {
                var oldSprint = sprintMapper.selectById(issue.getSprintId());
                oldSprintId = String.valueOf(issue.getSprintId());
                oldSprintName = oldSprint != null ? oldSprint.getName() : null;
            }
            String newSprintId = null;
            String newSprintName = null;
            if (dto.getSprintId() != 0) {
                var newSprint = sprintMapper.selectById(dto.getSprintId());
                newSprintId = String.valueOf(dto.getSprintId());
                newSprintName = newSprint != null ? newSprint.getName() : null;
            }
            recordActivity(id, currentUserId, "updated", "sprint", oldSprintId, newSprintId, oldSprintName, newSprintName);
            // 0 means "clear sprint" → set to null (DB convention: sprint_id IS NULL for Backlog)
            issue.setSprintId(dto.getSprintId() == 0 ? null : dto.getSprintId());
            // 收集迭代变更
            fieldChanges.put("sprint", new String[]{oldSprintName, newSprintName});
        }
        if (dto.getParentId() != null) {
            Long oldParentId = issue.getParentId();
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
            // 收集父工单变更
            fieldChanges.put("parent", new String[]{oldParentKey, newParentKey});

            // parentId 变更：刷新旧父和新父的祖先链
            Long newParentId = dto.getParentId() == 0 ? null : dto.getParentId();
            if (oldParentId != null && oldParentId != 0) {
                ancestorRefreshService.refreshAncestorChain(oldParentId);
            }
            if (newParentId != null) {
                ancestorRefreshService.refreshAncestorChain(newParentId);
            }
        }
        if (Boolean.TRUE.equals(dto.getClearDueDate())) {
            // 显式清空截止日期
            String oldDueDateStr = issue.getDueDate() != null ? issue.getDueDate().toString() : null;
            if (oldDueDateStr != null) {
                recordActivity(id, currentUserId, "updated", "due_date", oldDueDateStr, null);
                fieldChanges.put("due_date", new String[]{oldDueDateStr, null});
            }
            issue.setDueDate(null);
        } else if (dto.getDueDate() != null) {
            String oldDueDateStr = issue.getDueDate() != null ? issue.getDueDate().toString() : null;
            String newDueDateStr = dto.getDueDate().toString();
            recordActivity(id, currentUserId, "updated", "due_date", oldDueDateStr, newDueDateStr);
            issue.setDueDate(dto.getDueDate());
            // 收集截止日期变更
            fieldChanges.put("due_date", new String[]{oldDueDateStr, newDueDateStr});
        }
        if (Boolean.TRUE.equals(dto.getClearEstimatedHours())) {
            // 显式清空预估工时
            String oldHoursStr = issue.getEstimatedHours() != null ? issue.getEstimatedHours() + "h" : null;
            if (oldHoursStr != null) {
                recordActivity(id, currentUserId, "updated", "estimated_hours", oldHoursStr, null);
                fieldChanges.put("estimated_hours", new String[]{oldHoursStr, null});
            }
            issue.setEstimatedHours(null);
            // 清空预估工时：刷新祖先链的派生属性
            if (issue.getParentId() != null && issue.getParentId() != 0) {
                ancestorRefreshService.refreshAncestorChain(issue.getParentId());
            }
        } else if (dto.getEstimatedHours() != null) {
            recordActivity(id, currentUserId, "updated", "estimated_hours",
                    issue.getEstimatedHours() != null ? issue.getEstimatedHours() + "h" : null,
                    dto.getEstimatedHours() + "h");
            issue.setEstimatedHours(dto.getEstimatedHours());

            // 预估工时变更：刷新祖先链的派生属性
            if (issue.getParentId() != null && issue.getParentId() != 0) {
                ancestorRefreshService.refreshAncestorChain(issue.getParentId());
            }
        }
        if (dto.getCustomFields() != null) {
            // 保存自定义字段值到 EAV 表（单一数据源）
            Map<Long, String> fieldValues = new java.util.HashMap<>();
            for (Map.Entry<String, String> entry : dto.getCustomFields().entrySet()) {
                Long fieldId = parseFieldId(entry.getKey());
                fieldValues.put(fieldId, entry.getValue());
            }
            customFieldService.saveValues(issue.getId(), fieldValues,
                    issue.getIssueType(), issue.getProjectId(),
                    CustomFieldValidateMode.PARTIAL);
        }

        issueMapper.updateById(issue);

        // 触发 on-field-changed 自动化规则（通过事件，解耦）
        fireFieldChangeRules(issue, dto, oldValues);

        // 发布合并的字段变更通知事件（事务提交后触发，一次操作只产生一条通知）
        if (!fieldChanges.isEmpty()) {
            eventPublisher.publishEvent(new IssueNotificationEvent.MultiFieldUpdated(
                    issue, fieldChanges, currentUserId));
        }

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "issue_updated"));

        return new UpdateResult(issue, statusAutoReset);
    }

    /**
     * 软删除 Issue
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Issue issue = getById(id);
        // 归档项目不允许删除工单
        projectService.assertProjectActive(issue.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long parentId = issue.getParentId();
        Long projectId = issue.getProjectId();

        // 断开子工单的父引用（将子工单 parent_id 置为 NULL），防止产生孤儿引用
        issueMapper.clearParentId(id);

        // 清理通知静音记录（工单删除后静音无意义，避免孤立数据残留）
        mutedThreadService.deleteByResource("issue", id);

        // 先记录活动（deleteById 后逻辑删除字段被填充，查询会过滤掉）
        recordActivity(id, currentUserId, "deleted", null, null, null);
        // 使用 MyBatis-Plus 逻辑删除（自动设置 deleted_at = NOW()）
        issueMapper.deleteById(id);

        // 实时推送删除事件 — 通知正在查看列表/详情的用户
        eventPublisher.publishEvent(new IssueNotificationEvent.Deleted(issue, currentUserId));

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(projectId, "issue_deleted"));

        // 删除后刷新父工单的派生属性
        if (parentId != null && parentId != 0) {
            ancestorRefreshService.refreshAncestorChain(parentId);
        }
    }

    // ========== 移动到其他项目 ==========

    /**
     * 将工单移动到目标项目。
     * <p>
     * 流程：
     * 1. 权限校验（源项目 issue:move + 目标项目 issue:create）
     * 2. 重新分配 issue_key（目标项目序号递增）
     * 3. 字段清理（Sprint 置空、Assignee 校验、自定义字段清理）
     * 4. 关联数据同步（time_entry project_id 更新）
     * 5. 活动记录
     * 6. 通知触发
     */
    @Transactional(rollbackFor = Exception.class)
    public Issue moveToProject(Long issueId, MoveIssueDTO dto) {
        Issue issue = getById(issueId);
        Long sourceProjectId = issue.getProjectId();
        Long targetProjectId = dto.getTargetProjectId();

        // 不能移动到同一个项目
        if (sourceProjectId.equals(targetProjectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单已在该项目中，无需移动");
        }

        // 源项目和目标项目都不能是归档状态
        projectService.assertProjectActive(sourceProjectId);
        projectService.assertProjectActive(targetProjectId);

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 权限校验：源项目需要 issue:move，目标项目需要 issue:create
        if (!permissionService.hasPermission(currentUserId, sourceProjectId, "issue:move")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "您没有在源项目中移动工单的权限");
        }
        if (!permissionService.hasPermission(currentUserId, targetProjectId, "issue:create")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "您没有在目标项目中创建工单的权限");
        }

        // 获取源/目标项目信息
        var sourceProject = projectService.getById(sourceProjectId);
        var targetProject = projectService.getById(targetProjectId);
        if (targetProject == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标项目不存在");
        }

        // 生成新的 issue_key
        String oldIssueKey = issue.getIssueKey();
        int newSeq = projectService.nextIssueSequence(targetProjectId);
        String newIssueKey = targetProject.getKey() + "-" + newSeq;

        // 字段清理：Sprint 置空（不同项目的 Sprint 不通用）
        Long oldSprintId = issue.getSprintId();
        issue.setSprintId(null);

        // 字段清理：如果 assignee 不是目标项目成员，置空
        Long oldAssigneeId = issue.getAssigneeId();
        if (oldAssigneeId != null && !projectService.isProjectMember(oldAssigneeId, targetProjectId)) {
            issue.setAssigneeId(null);
        }

        // 更新核心字段
        issue.setProjectId(targetProjectId);
        issue.setIssueKey(newIssueKey);

        // 子工单处理：清理 parentId（如果父工单不在目标项目中）
        if (issue.getParentId() != null) {
            Issue parent = issueMapper.selectById(issue.getParentId());
            if (parent == null || !parent.getProjectId().equals(targetProjectId)) {
                issue.setParentId(null);
            }
        }

        // 保存旧 Key 到历史表，实现 Key 重定向
        IssueKeyHistory keyHistory = new IssueKeyHistory();
        keyHistory.setIssueId(issueId);
        keyHistory.setOldKey(oldIssueKey);
        keyHistory.setNewKey(newIssueKey);
        keyHistory.setChangedBy(currentUserId);
        issueKeyHistoryMapper.insert(keyHistory);

        // 状态兼容性检查：当前 statusId 在目标项目工作流中是否可达
        boolean statusValid = workflowService.isStatusInWorkflow(
                targetProjectId, issue.getIssueType(), issue.getStatusId());
        if (!statusValid) {
            // 自动回退到系统默认状态（对标 YouTrack：移动后状态不兼容时重置为默认值）
            var defaultStatus = workflowService.getDefaultStatus();
            if (defaultStatus != null) {
                Long oldStatusId = issue.getStatusId();
                issue.setStatusId(defaultStatus.getId());
                String oldStatusName = statusCacheHelper.getStatusName(oldStatusId);
                String newStatusName = defaultStatus.getLocalizedName();
                recordActivity(issueId, currentUserId, "status_reset", "status",
                        oldStatusName, newStatusName);
                log.info("Issue {} moved to project {}: status auto-reset from {} to default ({})",
                        issueId, targetProjectId, oldStatusName, newStatusName);
            }
        }

        issueMapper.updateById(issue);

        // 自定义字段清理：移除不适用于目标项目的字段值
        customFieldService.removeOrphanValues(issueId, issue.getIssueType(), targetProjectId);

        // 关联数据：更新 time_entry 的 project_id
        timeEntryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.trackflow.timeentry.entity.TimeEntry>()
                        .eq(com.trackflow.timeentry.entity.TimeEntry::getIssueId, issueId)
                        .set(com.trackflow.timeentry.entity.TimeEntry::getProjectId, targetProjectId));

        // 活动记录
        String sourceProjectName = sourceProject != null ? sourceProject.getName() : String.valueOf(sourceProjectId);
        String targetProjectName = targetProject.getName();
        recordActivity(issueId, currentUserId, "moved_to_project", "project",
                sourceProjectName, targetProjectName);

        // Sprint 清空活动记录（如果原来有值）
        if (oldSprintId != null) {
            String oldSprintName = null;
            var oldSprint = sprintMapper.selectById(oldSprintId);
            if (oldSprint != null) oldSprintName = oldSprint.getName();
            recordActivity(issueId, currentUserId, "updated", "sprint",
                    String.valueOf(oldSprintId), null, oldSprintName, null);
        }

        // Assignee 清空活动记录（如果因移动被清空）
        if (oldAssigneeId != null && issue.getAssigneeId() == null) {
            String oldAssigneeName = getUserDisplayName(oldAssigneeId);
            recordActivity(issueId, currentUserId, "assigned", "assignee", oldAssigneeName, null);
        }

        // issue_key 变更活动
        recordActivity(issueId, currentUserId, "updated", "issue_key", oldIssueKey, newIssueKey);

        // 失效两个项目的缓存
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sourceProjectId, "issue_moved"));
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(targetProjectId, "issue_moved"));

        // 通知：通知工单相关人
        eventPublisher.publishEvent(new IssueNotificationEvent.Moved(issue, sourceProjectId, targetProjectId, currentUserId));

        log.info("Issue {} moved from project {} ({}) to project {} ({}). Key: {} → {}",
                issueId, sourceProjectId, sourceProjectName, targetProjectId, targetProjectName,
                oldIssueKey, newIssueKey);

        return issue;
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
        List<ChildIssueRow> rows = issueMapper.selectChildrenByParentId(parentId);
        return rows.stream().map(row -> {
            ChildIssueVO vo = new ChildIssueVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setIssueKey(row.getIssueKey());
            vo.setTitle(row.getTitle());
            vo.setIssueType(row.getIssueType());
            vo.setPriority(row.getPriority());
            vo.setStatusName(row.getStatusName());
            vo.setStatusColor(row.getStatusColor());
            vo.setStatusCategory(row.getStatusCategory());
            vo.setAssigneeName(row.getAssigneeName());
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
        return executeBatchWithSilent(issueIds, permissionCode, action, operationName, false);
    }

    /**
     * 支持静默模式的批量操作执行引擎。
     * silent=true 时通知事件监听器会跳过通知发送。
     */
    private BatchOperationResultVO executeBatchWithSilent(
            List<Long> issueIds,
            String permissionCode,
            BatchIssueAction action,
            String operationName,
            boolean silent) {

        if (silent) {
            NotificationContext.setSilent(true);
        }
        try {
            return doExecuteBatch(issueIds, permissionCode, action, operationName);
        } finally {
            if (silent) {
                NotificationContext.clear();
            }
        }
    }

    /**
     * 批量操作核心逻辑。
     */
    private BatchOperationResultVO doExecuteBatch(
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
     * 获取批量操作中每个状态的可达性信息。
     * 对选中的所有工单，统计每个状态可被多少个工单转换到。
     */
    public List<BatchAvailableStatusVO> getBatchAvailableTransitions(List<Long> issueIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 获取所有状态
        List<IssueStatus> allStatuses = statusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().orderByAsc(IssueStatus::getSortOrder));

        // 获取每个工单的可用转换
        // Key: statusId, Value: 可以转换到此状态的工单数量
        Map<Long, Integer> reachabilityMap = new java.util.HashMap<>();
        for (IssueStatus status : allStatuses) {
            reachabilityMap.put(status.getId(), 0);
        }

        // 收集选中工单的当前状态（排除当前状态本身）
        Set<Long> currentStatusIds = new java.util.HashSet<>();

        for (Long issueId : issueIds) {
            try {
                Issue issue = getById(issueId);
                currentStatusIds.add(issue.getStatusId());
                List<IssueStatus> available = workflowService.getAvailableTransitions(issue, currentUserId);
                for (IssueStatus s : available) {
                    reachabilityMap.merge(s.getId(), 1, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("获取工单可用转换失败 issueId={}", issueId, e);
                // 跳过无法访问的工单
            }
        }

        int totalCount = issueIds.size();
        List<BatchAvailableStatusVO> result = new java.util.ArrayList<>();
        for (IssueStatus status : allStatuses) {
            int reachable = reachabilityMap.getOrDefault(status.getId(), 0);
            // 排除没有任何工单能转换到的状态（除非是当前状态，也排除）
            if (reachable == 0 && currentStatusIds.contains(status.getId())) {
                continue; // 当前状态不需要显示在目标列表中
            }
            if (reachable == 0) {
                continue; // 完全不可达的状态不显示
            }
            BatchAvailableStatusVO vo = new BatchAvailableStatusVO();
            vo.setId(String.valueOf(status.getId()));
            vo.setName(status.getName());
            vo.setColor(status.getColor());
            vo.setCategory(status.getCategory());
            vo.setIsClosed(status.getIsClosed());
            vo.setSortOrder(status.getSortOrder());
            vo.setReachableCount(reachable);
            vo.setTotalCount(totalCount);
            result.add(vo);
        }

        return result;
    }

    /**
     * 批量状态转换（带乐观锁 + 备注支持）。
     *
     * @param issueIds 要转换的工单 ID 列表
     * @param statusId 目标状态 ID
     * @param comment  可选备注（记录到活动日志）
     * @param versions 乐观锁版本映射（issueId → version），为 null 时跳过版本校验
     * @param silent   静默模式，为 true 时不发送通知
     */
    public BatchOperationResultVO batchTransitStatus(List<Long> issueIds, Long statusId,
                                                     String comment, Map<Long, Integer> versions, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:change_status", (issue, userId) -> {
            if (!workflowService.isTransitionAllowed(issue, statusId, userId)) {
                return "工作流不允许此状态转换";
            }
            Integer expectedVersion = versions != null ? versions.get(issue.getId()) : null;
            transitStatus(issue.getId(), statusId, comment, null, false, expectedVersion, true);
            return null;
        }, "状态转换", silent);
    }

    /**
     * 批量分配
     */
    public BatchOperationResultVO batchAssign(List<Long> issueIds, Long assigneeId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:assign", (issue, userId) -> {
            assign(issue.getId(), assigneeId);
            return null;
        }, "分配", silent);
    }

    /**
     * 批量更新 Sprint
     */
    public BatchOperationResultVO batchUpdateSprint(List<Long> issueIds, Long sprintId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            UpdateIssueDTO dto = new UpdateIssueDTO();
            dto.setSprintId(sprintId);
            update(issue.getId(), dto);
            return null;
        }, "Sprint移动", silent);
    }

    /**
     * 批量更新优先级
     */
    public BatchOperationResultVO batchUpdatePriority(List<Long> issueIds, String priority, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            UpdateIssueDTO dto = new UpdateIssueDTO();
            dto.setPriority(priority);
            update(issue.getId(), dto);
            return null;
        }, "优先级变更", silent);
    }

    /**
     * 批量添加标签
     */
    public BatchOperationResultVO batchAddTag(List<Long> issueIds, Long tagId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            tagService.addTagToIssue(issue.getId(), tagId);
            return null;
        }, "添加标签", silent);
    }

    /**
     * 批量移除标签
     */
    public BatchOperationResultVO batchRemoveTag(List<Long> issueIds, Long tagId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            tagService.removeTagFromIssue(issue.getId(), tagId);
            return null;
        }, "移除标签", silent);
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
                DeletedIssueRow row = issueMapper.selectByIdIgnoreDeleted(issueId);
                if (row == null || row.getDeletedAt() == null) {
                    result.addFailure(issueId, "?", "工单不在回收站中");
                    continue;
                }
                Long projectId = row.getProjectId();
                if (!permissionService.hasPermission(currentUserId, projectId, "issue:delete")) {
                    String key = row.getIssueKey() != null ? row.getIssueKey() : "?";
                    result.addFailure(issueId, key, "无恢复权限");
                    continue;
                }
                issueMapper.restoreById(issueId);
                recordActivity(issueId, currentUserId, "restored", null, null, null);
                // 发布恢复通知事件
                Issue restoredIssue = issueMapper.selectById(issueId);
                if (restoredIssue != null) {
                    eventPublisher.publishEvent(new IssueNotificationEvent.Restored(restoredIssue, currentUserId));
                }
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
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatus(Long id, Long newStatusId, String comment) {
        return transitStatus(id, newStatusId, comment, null, false, null, false);
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
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet) {
        return transitStatus(id, newStatusId, comment, assigneeId, assigneeExplicitlySet, null, false);
    }

    /**
     * 状态变更（带乐观锁版本校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet,
                              Integer expectedVersion) {
        return transitStatus(id, newStatusId, comment, assigneeId, assigneeExplicitlySet, expectedVersion, false);
    }

    /**
     * 状态变更（跳过工作流校验 - 仅限撤销操作内部调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatusSkipWorkflow(Long id, Long newStatusId, String comment) {
        return transitStatus(id, newStatusId, comment, null, false, null, true);
    }

    /**
     * 状态变更内部实现
     *
     * @param skipWorkflowCheck true = 跳过工作流规则校验（仅用于撤销操作，目标状态已在 Controller 中校验为上一状态）
     * @return 动作执行结果摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet,
                              Integer expectedVersion, boolean skipWorkflowCheck) {
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

        // 工作流校验（除非明确跳过，如撤销操作已在 Controller 中验证过目标状态）
        if (!skipWorkflowCheck) {
            if (!workflowService.isTransitionAllowed(issue, newStatusId, currentUserId)) {
                throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_DENIED,
                        "当前角色不允许执行此状态转换");
            }
        }

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
        String oldStatusDisplayName = oldStatus != null ? oldStatus.getLocalizedName() : String.valueOf(oldStatusId);
        String newStatusDisplayName = newStatus.getLocalizedName();
        recordActivity(id, currentUserId, "status_changed", "status",
                oldStatusDisplayName, newStatusDisplayName);

        // 通知报告人+负责人状态已变更 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.StatusChanged(issue, oldStatusId, newStatusId, currentUserId));

        // 如果是转换到 cancelled 类别，额外发布取消事件（供外部集成模块监听）
        if ("cancelled".equals(newStatus.getCategory())) {
            eventPublisher.publishEvent(new IssueNotificationEvent.Cancelled(issue, currentUserId));
        }

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "issue_status_changed"));

        // 如果带了评论，同时添加评论
        if (comment != null && !comment.isBlank()) {
            addComment(id, comment);
        }

        // 调用 TransitionActionEngine 执行自动化动作（auto-assign 等）
        ActionExecutionResult actionResult = transitionActionEngine.execute(issue, oldStatusId, newStatusId,
                currentUserId, assigneeId, assigneeExplicitlySet);

        // 状态变更后：刷新祖先链的派生属性（影响 childClosedCount）
        if (issue.getParentId() != null && issue.getParentId() != 0) {
            ancestorRefreshService.refreshAncestorChain(issue.getParentId());
        }

        return actionResult;
    }

    /**
     * 获取指定工单最近一次状态变更的活动记录。
     *
     * @param issueId Issue ID
     * @return 最近一次状态变更记录，无历史时返回 null
     */
    public IssueActivity getLastStatusChange(Long issueId) {
        return activityMapper.selectLastStatusChange(issueId);
    }

    /**
     * 分配 Issue
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, Long assigneeId) {
        Issue issue = getById(id);
        // 归档项目不允许分配工单
        projectService.assertProjectActive(issue.getProjectId());
        // 校验 assignee 是否为有效的项目成员
        validateAssignee(assigneeId, issue.getProjectId());

        Long normalizedAssigneeId = normalizeAssigneeId(assigneeId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 存储用户显示名快照（而非 userId），确保历史记录不可变
        String oldAssigneeName = getUserDisplayName(issue.getAssigneeId());
        String newAssigneeName = getUserDisplayName(normalizedAssigneeId);
        recordActivity(id, currentUserId, "assigned", "assignee", oldAssigneeName, newAssigneeName);
        issue.setAssigneeId(normalizedAssigneeId);
        issueMapper.updateById(issue);

        // 通知被分配人（仅当实际分配给某人时）— 事务提交后触发
        if (normalizedAssigneeId != null) {
            eventPublisher.publishEvent(new IssueNotificationEvent.Assigned(issue, normalizedAssigneeId, currentUserId));
        }

        // 失效 Dashboard 缓存（影响工作量分布） — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "issue_assigned"));
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

    @Transactional(rollbackFor = Exception.class)
    public IssueComment addComment(Long issueId, String content) {
        return addComment(issueId, content, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public IssueComment addComment(Long issueId, String content, List<Long> visibleToGroupIds) {
        // 归档项目不允许添加评论
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        IssueComment comment = new IssueComment();
        comment.setIssueId(issueId);
        comment.setUserId(currentUserId);
        comment.setContent(content);
        comment.setSource("web");
        // 设置可见性：空列表视为 null（全体可见）
        if (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) {
            comment.setVisibleToGroupIds(visibleToGroupIds);
        }
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        recordActivity(issueId, currentUserId, "commented", null, null, null);

        // 通知报告人+负责人+之前评论者 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Commented(issue, currentUserId));

        // 解析评论中的 @mention 并通知被提及的用户 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Mentioned(issue, content, currentUserId));

        return comment;
    }

    @Transactional(rollbackFor = Exception.class)
    public IssueComment updateComment(Long issueId, Long commentId, String newContent) {
        return updateComment(issueId, commentId, newContent, null, false);
    }

    /**
     * 更新评论内容和/或可见性。
     *
     * @param issueId            工单 ID
     * @param commentId          评论 ID
     * @param newContent         新内容
     * @param visibleToGroupIds  可见性组列表（null=不修改，空列表=移除限制）
     * @param updateVisibility   是否更新可见性（区分 null="不修改" 与 "请求体中未传该字段"）
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueComment updateComment(Long issueId, Long commentId, String newContent,
                                       List<Long> visibleToGroupIds, boolean updateVisibility) {
        // 归档项目不允许编辑评论
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        // 权限校验：作者本人 OR 拥有 issue:manage_comments 权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!comment.getUserId().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权编辑他人评论");
            }
        }

        comment.setContent(newContent);
        if (updateVisibility) {
            // 空列表视为 null（移除限制）
            comment.setVisibleToGroupIds(
                    visibleToGroupIds != null && !visibleToGroupIds.isEmpty() ? visibleToGroupIds : null);
        }
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);

        recordActivity(issueId, currentUserId, "comment_updated", null, null, null);

        return comment;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long issueId, Long commentId) {
        // 归档项目不允许删除评论
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        // 权限校验：作者本人 OR 拥有 issue:manage_comments 权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!comment.getUserId().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权删除他人评论");
            }
        }

        commentMapper.deleteById(commentId);

        recordActivity(issueId, currentUserId, "comment_deleted", null, null, null);
    }

    // ========== 附件 ==========

    /**
     * 查询附件列表（已按可见性过滤）
     * - 公开附件（visibleToGroupIds=NULL）：所有项目成员可见
     * - 私有附件：仅上传者、指定组成员、拥有 issue:read_private 权限的用户可见
     */
    public List<IssueAttachment> listAttachments(Long issueId) {
        List<IssueAttachment> allAttachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId)
        );
        return filterAttachmentsByVisibility(allAttachments, issueId);
    }

    /**
     * 按可见性规则过滤附件列表
     */
    private List<IssueAttachment> filterAttachmentsByVisibility(List<IssueAttachment> attachments, Long issueId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            // 未认证用户只能看公开附件
            return attachments.stream()
                    .filter(a -> a.getVisibleToGroupIds() == null || a.getVisibleToGroupIds().isEmpty())
                    .toList();
        }

        // 快速路径：无私有附件则全量返回
        boolean hasPrivate = attachments.stream()
                .anyMatch(a -> a.getVisibleToGroupIds() != null && !a.getVisibleToGroupIds().isEmpty());
        if (!hasPrivate) {
            return attachments;
        }

        // 获取 issue 所属项目以检查权限
        Issue issue = getById(issueId);
        // 拥有 issue:read_private 权限的用户可以看到所有附件
        if (permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:read_private")) {
            return attachments;
        }

        // 获取当前用户的组 ID
        List<Long> userGroupIds = userGroupMemberMapper.selectGroupIdsByUserId(currentUserId);
        Set<Long> userGroupIdSet = userGroupIds != null ? Set.copyOf(userGroupIds) : Set.of();

        return attachments.stream()
                .filter(a -> {
                    // 公开附件
                    if (a.getVisibleToGroupIds() == null || a.getVisibleToGroupIds().isEmpty()) {
                        return true;
                    }
                    // 上传者本人始终可见
                    if (a.getUploadedBy() != null && a.getUploadedBy().equals(currentUserId)) {
                        return true;
                    }
                    // 当前用户属于附件指定的任一组
                    return a.getVisibleToGroupIds().stream().anyMatch(userGroupIdSet::contains);
                })
                .toList();
    }

    /**
     * 检查当前用户是否有权访问指定附件
     */
    public boolean canAccessAttachment(Long attachmentId) {
        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            return false;
        }
        // 公开附件
        if (attachment.getVisibleToGroupIds() == null || attachment.getVisibleToGroupIds().isEmpty()) {
            return true;
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        // 上传者本人
        if (attachment.getUploadedBy() != null && attachment.getUploadedBy().equals(currentUserId)) {
            return true;
        }
        // issue:read_private 权限
        Issue issue = getById(attachment.getIssueId());
        if (permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:read_private")) {
            return true;
        }
        // 组成员检查
        List<Long> userGroupIds = userGroupMemberMapper.selectGroupIdsByUserId(currentUserId);
        if (userGroupIds == null || userGroupIds.isEmpty()) {
            return false;
        }
        Set<Long> userGroupIdSet = Set.copyOf(userGroupIds);
        return attachment.getVisibleToGroupIds().stream().anyMatch(userGroupIdSet::contains);
    }

    /**
     * 根据文件路径查找附件记录（用于文件下载时的权限校验）
     */
    public IssueAttachment findByFilePath(String filePath) {
        return attachmentMapper.selectOne(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getFilePath, filePath)
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
        projectService.assertProjectAccessible(currentUserId, Long.parseLong(detail.getProjectId()));
        return detail;
    }

    /**
     * 获取增强版 Issue 详情 —— 单次 SQL JOIN 替代 N+1 查询
     */
    public IssueDetailVO getDetail(Long id) {
        IssueDetailRow row = issueMapper.selectDetailById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }

        IssueDetailVO vo = new IssueDetailVO();
        vo.setId(String.valueOf(row.getId()));
        vo.setProjectId(String.valueOf(row.getProjectId()));
        vo.setProjectName(row.getProjectName());
        vo.setProjectStatus(row.getProjectStatus());
        vo.setIssueKey(row.getIssueKey());
        vo.setTitle(row.getTitle());
        vo.setDescription(row.getDescription());
        vo.setIssueType(row.getIssueType());
        vo.setStatusId(String.valueOf(row.getStatusId()));
        vo.setPriority(row.getPriority());
        vo.setAssigneeId(row.getAssigneeId() != null ? String.valueOf(row.getAssigneeId()) : null);
        vo.setAssigneeName(row.getAssigneeName());
        vo.setAssigneeAvatarUrl(row.getAssigneeAvatarUrl());
        vo.setReporterId(String.valueOf(row.getReporterId()));
        vo.setReporterName(row.getReporterName());
        vo.setSprintId(row.getSprintId() != null ? String.valueOf(row.getSprintId()) : null);
        vo.setSprintName(row.getSprintName());
        vo.setParentId(row.getParentId() != null ? String.valueOf(row.getParentId()) : null);
        vo.setParentKey(row.getParentKey());

        vo.setDueDate(row.getDueDate());
        vo.setEstimatedHours(row.getEstimatedHours());
        vo.setSpentHours(row.getSpentHours());
        vo.setDerivedEstimatedHours(row.getDerivedEstimatedHours());
        vo.setDerivedSpentHours(row.getDerivedSpentHours());
        vo.setResolvedAt(row.getResolvedAt());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());

        // 创建者/更新者信息
        vo.setCreatedById(row.getCreatedById() != null ? String.valueOf(row.getCreatedById()) : null);
        vo.setCreatedByName(row.getCreatedByName());
        vo.setUpdatedById(row.getUpdatedById() != null ? String.valueOf(row.getUpdatedById()) : null);
        vo.setUpdatedByName(row.getUpdatedByName());

        // 乐观锁版本号
        vo.setVersion(row.getVersion());

        // 状态对象
        if (row.getStatusName() != null) {
            IssueStatusVO statusVO = new IssueStatusVO();
            statusVO.setId(String.valueOf(row.getStatusId()));
            statusVO.setName(row.getStatusName());
            statusVO.setDisplayName(row.getStatusDisplayName());
            statusVO.setCode(row.getStatusCode());
            statusVO.setColor(row.getStatusColor());
            statusVO.setCategory(row.getStatusCategory());
            statusVO.setIsDefault(row.getStatusIsDefault());
            statusVO.setIsClosed(row.getStatusIsClosed());
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
        List<CommentRow> rows = issueMapper.selectCommentsWithUser(issueId);

        // 获取当前用户信息用于可见性过滤
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Long> currentUserGroupIds = userGroupMemberMapper.selectGroupIdsByUserId(currentUserId);

        // 获取工单的 projectId 用于权限检查
        Issue issue = getById(issueId);
        boolean canManageComments = permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments");

        // 过滤掉当前用户无权查看的评论
        List<CommentRow> visibleRows = rows.stream()
                .filter(row -> isCommentVisibleToUser(row, currentUserId, currentUserGroupIds, canManageComments))
                .toList();

        // 收集所有需要解析的组 ID，批量查询组名称
        Set<Long> allGroupIds = visibleRows.stream()
                .filter(row -> row.getVisibleToGroupIds() != null && !row.getVisibleToGroupIds().isEmpty())
                .flatMap(row -> row.getVisibleToGroupIds().stream())
                .collect(Collectors.toSet());

        Map<Long, String> groupNameMap = Collections.emptyMap();
        if (!allGroupIds.isEmpty()) {
            groupNameMap = userGroupMapper.selectBatchIds(allGroupIds).stream()
                    .collect(Collectors.toMap(
                            com.trackflow.system.entity.UserGroup::getId,
                            com.trackflow.system.entity.UserGroup::getName));
        }

        Map<Long, String> finalGroupNameMap = groupNameMap;
        return visibleRows.stream().map(row -> {
            IssueCommentVO vo = new IssueCommentVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setIssueId(String.valueOf(row.getIssueId()));
            vo.setUserId(String.valueOf(row.getUserId()));
            vo.setUserName(row.getUserName());
            vo.setUserAvatar(row.getUserAvatar());
            vo.setContent(row.getContent());
            vo.setSource(row.getSource());
            vo.setCreatedAt(row.getCreatedAt());
            vo.setUpdatedAt(row.getUpdatedAt());
            // 判断是否被编辑过：updated_at 比 created_at 晚超过 1 秒
            vo.setIsEdited(row.getCreatedAt() != null && row.getUpdatedAt() != null
                    && row.getUpdatedAt().isAfter(row.getCreatedAt().plusSeconds(1)));
            // 填充可见性信息
            if (row.getVisibleToGroupIds() != null && !row.getVisibleToGroupIds().isEmpty()) {
                vo.setVisibleToGroupIds(row.getVisibleToGroupIds().stream()
                        .map(String::valueOf).toList());
                vo.setVisibleToGroupNames(row.getVisibleToGroupIds().stream()
                        .map(gid -> finalGroupNameMap.getOrDefault(gid, "未知组"))
                        .toList());
            }
            return vo;
        }).toList();
    }

    /**
     * 判断当前用户是否有权查看某条评论。
     * 规则：
     * 1. 评论无可见性限制（visibleToGroupIds 为 null/空）→ 全体可见
     * 2. 评论作者本人 → 始终可见
     * 3. 用户拥有 issue:manage_comments 权限 → 始终可见
     * 4. 用户所属的任一组在评论的 visibleToGroupIds 中 → 可见
     * 5. 否则 → 不可见
     */
    private boolean isCommentVisibleToUser(CommentRow row, Long currentUserId,
                                           List<Long> currentUserGroupIds, boolean canManageComments) {
        // 无限制：全体可见
        if (row.getVisibleToGroupIds() == null || row.getVisibleToGroupIds().isEmpty()) {
            return true;
        }
        // 作者本人始终可见
        if (row.getUserId().equals(currentUserId)) {
            return true;
        }
        // 管理评论权限用户始终可见
        if (canManageComments) {
            return true;
        }
        // 检查用户组是否有交集
        if (currentUserGroupIds == null || currentUserGroupIds.isEmpty()) {
            return false;
        }
        for (Long groupId : row.getVisibleToGroupIds()) {
            if (currentUserGroupIds.contains(groupId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取活动列表 —— 单次 JOIN 查询（消除 N+1）
     * assignee 字段的 old/new value 在 SQL 层自动解析为用户显示名
     */
    public List<IssueActivityVO> listActivitiesWithUser(Long issueId) {
        List<ActivityRow> rows = issueMapper.selectActivitiesWithUser(issueId);
        return rows.stream().map(row -> {
            IssueActivityVO vo = new IssueActivityVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setIssueId(String.valueOf(row.getIssueId()));
            vo.setUserId(String.valueOf(row.getUserId()));
            vo.setUserName(row.getUserName());
            vo.setUserAvatar(row.getUserAvatar());
            vo.setAction(row.getAction());
            vo.setFieldName(row.getFieldName());
            vo.setOldValue(row.getOldValue());
            vo.setNewValue(row.getNewValue());
            vo.setCreatedAt(row.getCreatedAt());
            return vo;
        }).toList();
    }

    // ========== 附件上传 ==========

    /**
     * 上传附件
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueAttachment uploadAttachment(Long issueId, MultipartFile file) {
        return uploadAttachment(issueId, file, null);
    }

    /**
     * 上传附件（支持私有上传）
     *
     * @param issueId 工单 ID
     * @param file 上传的文件
     * @param visibleToGroupIds 可见性限制组 ID 列表；null 或空表示公开
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueAttachment uploadAttachment(Long issueId, MultipartFile file, List<Long> visibleToGroupIds) {
        // 验证 Issue 存在
        Issue issue = getById(issueId);
        // 归档项目不允许上传附件
        projectService.assertProjectActive(issue.getProjectId());

        // ===== 安全校验 =====
        validateAttachmentFile(file);
        validateAttachmentCount(issueId);

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 清理文件名（去除路径遍历字符和特殊字符）
        String safeFileName = sanitizeFileName(file.getOriginalFilename());

        // 上传到 MinIO
        String folder = "issues/" + issueId + "/attachments";
        String filePath = minioService.upload(folder, safeFileName, file);

        // 保存 DB 记录
        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssueId(issueId);
        attachment.setFileName(safeFileName);
        attachment.setFilePath(filePath);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setUploadedBy(currentUserId);
        attachment.setVisibleToGroupIds(
                visibleToGroupIds != null && !visibleToGroupIds.isEmpty() ? visibleToGroupIds : null);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);

        // 记录活动
        recordActivity(issueId, currentUserId, "attachment_added", "attachment", null, safeFileName);

        // 发布通知事件
        eventPublisher.publishEvent(new IssueNotificationEvent.AttachmentAdded(issue, safeFileName, currentUserId));

        return attachment;
    }

    /**
     * 校验附件文件大小和类型
     */
    private void validateAttachmentFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        // 文件大小校验
        if (file.getSize() > attachmentConfig.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文件大小超出限制（最大 " + attachmentConfig.getMaxFileSizeReadable() + "）");
        }

        // 文件扩展名校验（黑名单模式）
        String extension = getFileExtension(file.getOriginalFilename());
        if (attachmentConfig.getBlockedExtensions().contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不允许上传此类型文件: ." + extension);
        }
    }

    /**
     * 校验单工单附件数量限制
     */
    private void validateAttachmentCount(Long issueId) {
        long count = attachmentMapper.selectCount(
                new LambdaQueryWrapper<IssueAttachment>().eq(IssueAttachment::getIssueId, issueId));
        if (count >= attachmentConfig.getMaxAttachmentsPerIssue()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该工单附件数量已达上限（最多 " + attachmentConfig.getMaxAttachmentsPerIssue() + " 个）");
        }
    }

    /**
     * 清理文件名：去除路径分隔符、路径遍历字符和特殊字符
     */
    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed";
        }
        // 去除路径分隔符（取最后一段作为文件名）
        String name = originalName;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        // 去除路径遍历和特殊字符
        name = name.replaceAll("\\.\\.", "_")
                   .replaceAll("[\\\\/:*?\"<>|]", "_");
        // 确保不为空
        if (name.isBlank()) {
            return "unnamed";
        }
        return name;
    }

    /**
     * 提取文件扩展名（不含点，小写）
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 删除附件
     * 只有附件上传者或拥有 issue:manage_attachments 权限的用户可删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long issueId, Long attachmentId) {
        // 归档项目不允许删除附件
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }

        // 所有权校验：非上传者需要 issue:manage_attachments 权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!attachment.getUploadedBy().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_attachments")) {
                throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只能删除自己上传的附件，或需要附件管理权限");
            }
        }

        // 从 MinIO 删除
        minioService.delete(attachment.getFilePath());
        // 从 DB 删除
        attachmentMapper.deleteById(attachmentId);

        recordActivity(issueId, currentUserId, "attachment_removed", "attachment", attachment.getFileName(), null);
    }

    /**
     * 更新附件可见性
     * 仅上传者或拥有 issue:manage_attachments 权限的用户可修改
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueAttachment updateAttachmentVisibility(Long issueId, Long attachmentId, List<Long> visibleToGroupIds) {
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        IssueAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }

        // 权限校验：上传者或 issue:manage_attachments
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!attachment.getUploadedBy().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_attachments")) {
                throw new BusinessException(ErrorCode.OWNERSHIP_REQUIRED, "只能修改自己上传的附件可见性，或需要附件管理权限");
            }
        }

        // 更新可见性
        List<Long> newVisibility = (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) ? visibleToGroupIds : null;
        attachment.setVisibleToGroupIds(newVisibility);
        attachmentMapper.updateById(attachment);

        // 记录活动
        String visibilityDesc = newVisibility == null ? "公开" : "限制可见";
        recordActivity(issueId, currentUserId, "attachment_visibility_changed", "attachment_visibility",
                null, attachment.getFileName() + " → " + visibilityDesc);

        return attachment;
    }

    // ========== 回收站 ==========

    /**
     * 回收站列表：查询指定项目中已删除的 Issue（分页）
     */
    public PageResult<IssueTrashVO> listTrash(Long projectId, int page, int pageSize) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(currentUserId, projectId);
        Page<TrashRow> p = new Page<>(page, pageSize);
        Page<TrashRow> result = issueMapper.selectTrashPage(p, projectId);

        List<IssueTrashVO> voList = result.getRecords().stream().map(row -> {
            IssueTrashVO vo = new IssueTrashVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setProjectId(String.valueOf(row.getProjectId()));
            vo.setIssueKey(row.getIssueKey());
            vo.setTitle(row.getTitle());
            vo.setIssueType(row.getIssueType());
            vo.setPriority(row.getPriority());
            vo.setAssigneeName(row.getAssigneeName());
            vo.setDeletedByName(row.getDeletedByName());
            vo.setDeletedAt(row.getDeletedAt());
            return vo;
        }).toList();

        return new PageResult<>(voList, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 恢复已删除的 Issue（从回收站还原）
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        DeletedIssueRow row = issueMapper.selectByIdIgnoreDeleted(id);
        if (row == null || row.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单不在回收站中");
        }
        Long projectId = row.getProjectId();
        projectService.assertProjectActive(projectId);

        int affected = issueMapper.restoreById(id);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "恢复失败，工单不在回收站中");
        }
        recordActivity(id, SecurityUtils.getCurrentUserId(), "restored", null, null, null);

        // 发布恢复通知事件（restoreById 已清除 deleted_at，此时可正常查询）
        Issue restoredIssue = issueMapper.selectById(id);
        if (restoredIssue != null) {
            eventPublisher.publishEvent(new IssueNotificationEvent.Restored(restoredIssue, SecurityUtils.getCurrentUserId()));
        }

        // 恢复后刷新父工单的派生属性
        Long parentId = row.getParentId();
        if (parentId != null && parentId != 0) {
            ancestorRefreshService.refreshAncestorChain(parentId);
        }
    }

    /**
     * 永久删除 Issue（物理删除），同时清理关联数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void permanentDelete(Long id) {
        DeletedIssueRow row = issueMapper.selectByIdIgnoreDeleted(id);
        if (row == null || row.getDeletedAt() == null) {
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

        // 清理通知静音记录（兜底，软删除时应已清理）
        mutedThreadService.deleteByResource("issue", id);

        // 物理删除工单
        issueMapper.permanentDeleteById(id);
    }

    /**
     * 获取已删除工单所属的 projectId（用于 @PreAuthorize SpEL）
     */
    public Long getDeletedIssueProjectId(Long issueId) {
        DeletedIssueRow row = issueMapper.selectByIdIgnoreDeleted(issueId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return row.getProjectId();
    }

    // ========== 内部方法 ==========

    /**
     * 校验 assigneeId 有效性：用户必须存在、未被禁用、且是指定项目的成员。
     * assigneeId 为 null 或 0 时跳过校验（表示取消分配）。
     */
    private void validateAssignee(Long assigneeId, Long projectId) {
        if (assigneeId == null || assigneeId == 0L) {
            return;
        }
        SysUser user = sysUserMapper.selectById(assigneeId);
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的负责人不存在");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的负责人已被禁用");
        }
        if (!projectService.isProjectMember(assigneeId, projectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的负责人不是该项目的成员");
        }
        // 校验被分配者必须拥有 issue:edit 权限（排除观察者等不可分配角色）
        if (!projectService.isAssignableMember(assigneeId, projectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的负责人角色不具备处理工单的权限，无法被分配");
        }
    }

    /**
     * 将 assigneeId = 0 转换为 null（0 是前端表示"取消分配"的约定值，DB 用 NULL 表示无负责人）。
     */
    private Long normalizeAssigneeId(Long assigneeId) {
        return (assigneeId != null && assigneeId == 0L) ? null : assigneeId;
    }

    /**
     * 获取用户显示名（用于活动记录快照）。
     * 返回用户 displayName，用户不存在或已删除时返回 null。
     */
    private String getUserDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getDisplayName() : null;
    }

    /**
     * 触发字段变更自动化规则：检查 DTO 中哪些字段被实际修改了，对每个变更字段触发规则。
     * 事件仅传递 ID + oldValue，规则引擎在事务提交后异步从 DB 重新加载最新实体执行。
     *
     * @param issue     已更新的工单实体
     * @param dto       更新请求 DTO
     * @param oldValues 字段名 → 旧值 Map（在字段赋值前收集）
     */
    private void fireFieldChangeRules(Issue issue, UpdateIssueDTO dto, Map<String, String> oldValues) {
        if (dto.getIssueType() != null) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(issue.getId(), issue.getProjectId(), "issue_type", oldValues.get("issue_type")));
        }
        if (dto.getPriority() != null) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(issue.getId(), issue.getProjectId(), "priority", oldValues.get("priority")));
        }
        if (dto.getAssigneeId() != null) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(issue.getId(), issue.getProjectId(), "assignee", oldValues.get("assignee")));
        }
        if (dto.getSprintId() != null) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(issue.getId(), issue.getProjectId(), "sprint", oldValues.get("sprint")));
        }
        if (dto.getTitle() != null) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(issue.getId(), issue.getProjectId(), "title", oldValues.get("title")));
        }
        if (dto.getDueDate() != null) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(issue.getId(), issue.getProjectId(), "due_date", oldValues.get("due_date")));
        }
    }

    private void recordActivity(Long issueId, Long userId, String action,
                                String fieldName, String oldValue, String newValue) {
        recordActivity(issueId, userId, action, fieldName, oldValue, newValue, null, null);
    }

    private void recordActivity(Long issueId, Long userId, String action,
                                String fieldName, String oldValue, String newValue,
                                String oldDisplayValue, String newDisplayValue) {
        // Skip no-op changes: if both old and new values are present and identical, don't record
        if (oldValue != null && newValue != null && oldValue.equals(newValue)
                && "updated".equals(action)) {
            return;
        }
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setOldDisplayValue(oldDisplayValue);
        activity.setNewDisplayValue(newDisplayValue);
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    /**
     * 将自定义字段 key（字符串形式的字段ID）解析为 Long。
     * 解析失败时抛出 BusinessException 而非静默忽略。
     */
    private Long parseFieldId(String key) {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段 ID 格式错误: " + key);
        }
    }
}


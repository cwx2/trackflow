package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.config.AttachmentConfig;
import com.trackflow.common.constant.IssueStatusCategory;
import com.trackflow.common.context.NotificationContext;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.service.MinioService;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.SqlUtils;
import com.trackflow.issue.util.IssuePriorityHelper;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.service.CustomFieldSortHelper;
import com.trackflow.customfield.service.CustomFieldValidateMode;
import com.trackflow.issue.dto.CreateIssueDTO;
import com.trackflow.issue.dto.CreateIssueLinkDTO;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.dto.MoveIssueDTO;
import com.trackflow.issue.dto.TransitStatusDTO;
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
import com.trackflow.issue.dto.BatchOperationResult;
import com.trackflow.issue.dto.TransitStatusResult;
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
import com.trackflow.automation.execution.AutomationActorRunner;

import java.time.Duration;
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
     * Update 操作结果，包含更新后的实体、附加标志和字段级警告
     */
    public record UpdateResult(Issue issue, boolean statusAutoReset, java.util.List<String> warnings) {
        public UpdateResult(Issue issue, boolean statusAutoReset) {
            this(issue, statusAutoReset, java.util.List.of());
        }
    }

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueAttachmentMapper attachmentMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueKeyHistoryMapper issueKeyHistoryMapper;
    private final com.trackflow.sprint.mapper.SprintMapper sprintMapper;
    private final com.trackflow.issue.mapper.IssueSprintMapper issueSprintMapper;
    private final ProjectService projectService;
    private final MinioService minioService;
    private final IssueConverter issueConverter;
    private final IssueLinkService issueLinkService;
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
    private final com.trackflow.board.mapper.BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final com.trackflow.issue.service.precheck.ClosePreCheckChain closePreCheckChain;
    private final com.trackflow.issue.mapper.IssueVisibilityUserMapper visibilityUserMapper;
    private final PriorityFieldService priorityFieldService;
    private final IssueTypeFieldService issueTypeFieldService;
    private final DueDateFieldService dueDateFieldService;
    private final IssueActivityService activityService;
    private final IssueCommentService commentService;
    private final IssueAttachmentService attachmentService;
    private final IssueVOAssembler issueVOAssembler;

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

        // 获取默认状态：优先从工作流初始状态配置中查找，再 fallback 到系统默认
        Long resolvedStatusId = null;

        // Step 1: 查询工作流初始状态配置（按 project+issueType 优先级匹配）
        Long workflowInitialStatusId = workflowService.getInitialStatusId(dto.getProjectId(), dto.getIssueType());
        if (workflowInitialStatusId != null) {
            IssueStatus workflowStatus = statusMapper.selectById(workflowInitialStatusId);
            if (workflowStatus != null) {
                resolvedStatusId = workflowStatus.getId();
            }
        }

        // Step 2: 如果工作流未配置，使用 issue_status 表的系统默认
        if (resolvedStatusId == null) {
            IssueStatus defaultStatus = statusMapper.selectOne(
                    new LambdaQueryWrapper<IssueStatus>().eq(IssueStatus::getIsDefault, true)
            );
            resolvedStatusId = defaultStatus != null ? defaultStatus.getId() : 1L;
        }

        // Step 3: 用户显式指定的 statusId 最高优先
        if (dto.getStatusId() != null) {
            IssueStatus specifiedStatus = statusMapper.selectById(dto.getStatusId());
            if (specifiedStatus != null) {
                resolvedStatusId = specifiedStatus.getId();
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的状态不存在: " + dto.getStatusId());
            }
        }

        Issue issue = new Issue();
        issue.setProjectId(dto.getProjectId());
        issue.setIssueKey(issueKey);
        issue.setTitle(dto.getTitle());
        issue.setDescription(dto.getDescription());
        issue.setIssueType(issueTypeFieldService.normalizeIssueType(dto.getIssueType(), dto.getProjectId()));
        issue.setStatusId(resolvedStatusId);
        issue.setPriority(dto.getPriority() != null ? dto.getPriority() : priorityFieldService.getDefaultPriority(dto.getProjectId()));
        // 校验 assignee 是否为有效的项目成员
        validateAssignee(dto.getAssigneeId(), dto.getProjectId());
        issue.setAssigneeId(normalizeAssigneeId(dto.getAssigneeId()));
        issue.setReporterId(currentUserId);
        // 如果未指定 sprintId，尝试使用项目默认 Sprint
        Long resolvedSprintId = dto.getSprintId();
        if (resolvedSprintId == null) {
            Long defaultSprintId = projectService.getProjectSettingAsLong(dto.getProjectId(), "defaultSprintId");
            if (defaultSprintId != null) {
                // 验证默认 Sprint 仍然有效（只有 PLANNED 或 ACTIVE 才接收新工单）
                var defaultSprint = sprintMapper.selectById(defaultSprintId);
                if (defaultSprint != null
                        && defaultSprint.getProjectId().equals(dto.getProjectId())
                        && (defaultSprint.getStatus() == com.trackflow.sprint.entity.SprintStatus.PLANNED
                            || defaultSprint.getStatus() == com.trackflow.sprint.entity.SprintStatus.ACTIVE)) {
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
        // Due Date 项目级校验：必填性检查（快速创建模式和子工单跳过）
        boolean skipRequiredChecks = Boolean.TRUE.equals(dto.getQuickCreate())
                || (dto.getParentId() != null && dto.getParentId() != 0);
        if (!skipRequiredChecks && dueDateFieldService.isRequiredForProject(dto.getProjectId()) && issue.getDueDate() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "截止日期为必填字段");
        }
        issue.setEstimatedHours(dto.getEstimatedHours());
        issue.setCreatedBy(currentUserId);

        issueMapper.insert(issue);

        // 同步 issue_sprint 关联表
        if (issue.getSprintId() != null) {
            IssueSprint relation = new IssueSprint();
            relation.setIssueId(issue.getId());
            relation.setSprintId(issue.getSprintId());
            relation.setCreatedAt(LocalDateTime.now());
            issueSprintMapper.insert(relation);
        }

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
        // 子工单继承：若创建子工单且用户未提供某个自定义字段值，从父工单继承
        boolean isSubIssue = dto.getParentId() != null && dto.getParentId() != 0;
        boolean isQuickCreate = Boolean.TRUE.equals(dto.getQuickCreate());
        if (isSubIssue) {
            Map<Long, String> parentFieldValues = customFieldService.getValues(dto.getParentId());
            if (parentFieldValues != null && !parentFieldValues.isEmpty()) {
                for (Map.Entry<Long, String> entry : parentFieldValues.entrySet()) {
                    userFieldValues.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
        // 应用默认值并校验必填字段
        // 子工单或快速创建模式使用 PARTIAL 模式：跳过必填校验（允许快速创建，仅标题必填）
        // 顶层工单使用 FULL 模式：所有必填字段必须有值
        if (isSubIssue || isQuickCreate) {
            // 子工单/快速创建：应用默认值但不强制必填，允许快速创建
            Map<Long, String> mergedFieldValues = customFieldService.applyDefaultsOnly(
                    userFieldValues, issue.getIssueType(), issue.getProjectId());
            if (!mergedFieldValues.isEmpty()) {
                customFieldService.saveValues(issue.getId(), mergedFieldValues,
                        issue.getIssueType(), issue.getProjectId(), CustomFieldValidateMode.PARTIAL);
            }
        } else {
            // 顶层工单：完整必填校验
            Map<Long, String> mergedFieldValues = customFieldService.applyDefaultsAndValidate(
                    userFieldValues, issue.getIssueType(), issue.getProjectId());
            if (!mergedFieldValues.isEmpty()) {
                customFieldService.saveValues(issue.getId(), mergedFieldValues,
                        issue.getIssueType(), issue.getProjectId());
            }
        }

        // 记录活动
        recordActivity(issue.getId(), currentUserId, "created", null, null, null);

        // 创建关联（如果创建时指定了 links）
        if (dto.getLinks() != null && !dto.getLinks().isEmpty()) {
            for (CreateIssueLinkDTO linkDto : dto.getLinks()) {
                try {
                    issueLinkService.createIssueLink(issue.getId(), linkDto);
                } catch (Exception e) {
                    // 关联创建失败不阻塞主工单创建，仅记录警告
                    log.warn("创建工单时建立关联失败: issueId={}, targetIssueId={}, linkType={}, error={}",
                            issue.getId(), linkDto.getTargetIssueId(), linkDto.getLinkType(), e.getMessage());
                }
            }
        }

        // 关联标签（如果创建时指定了 tagIds）
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            for (Long tagId : dto.getTagIds()) {
                try {
                    tagService.addTagToIssue(issue.getId(), tagId);
                } catch (Exception e) {
                    // 标签关联失败不阻塞主工单创建，仅记录警告
                    log.warn("创建工单时关联标签失败: issueId={}, tagId={}, error={}",
                            issue.getId(), tagId, e.getMessage());
                }
            }
        }

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
        // assigneeId: supports single or comma-separated (skipped when assignedToMe=true, which takes priority)
        if (!"true".equalsIgnoreCase(query.getAssignedToMe())) {
            applyFilter(wrapper, "assignee_id", query.getAssigneeId(), true);
        }
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

        // Tag filter: EXISTS subquery on issue_tag_relation (OR semantics for multiple tags)
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

        // hideResolved: exclude all is_closed=true statuses
        if ("true".equals(query.getHideResolved())) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.notIn("status_id", closedStatusIds);
            }
        }

        // onlyResolved: only show is_closed=true statuses
        if ("true".equals(query.getOnlyResolved())) {
            Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
            if (!closedStatusIds.isEmpty()) {
                wrapper.in("status_id", closedStatusIds);
            } else {
                // No closed statuses defined — return nothing
                wrapper.apply("1 = 0");
            }
        }

        // Special filters: overdue, dueSoon, reportedByMe (all exclude done/cancelled statuses)
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

        // dueAfter / dueBefore: 按截止日期范围筛选（用于日历 Widget 等场景）
        if (query.getDueAfter() != null) {
            wrapper.ge("due_date", query.getDueAfter());
        }
        if (query.getDueBefore() != null) {
            wrapper.le("due_date", query.getDueBefore());
        }

        // reportedByMe: reporter_id = current user
        if ("true".equals(query.getReportedByMe())) {
            wrapper.eq("reporter_id", currentUserId);
        }

        // assignedToMe: assignee_id = current user
        if ("true".equalsIgnoreCase(query.getAssignedToMe())) {
            wrapper.eq("assignee_id", currentUserId);
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
        boolean hasSpecialSort = false;
        if (sort != null && !sort.isBlank()) {
            // 解析排序方向（-fieldName 降序，fieldName 升序）
            boolean desc = sort.startsWith("-");
            String sortField = desc ? sort.substring(1) : sort;
            if (customFieldSortHelper.isCustomFieldSortKey(sortField)) {
                hasCustomFieldSort = customFieldSortHelper.applyCustomFieldSort(wrapper, sortField, !desc);
            } else if ("remaining".equals(sortField)) {
                // remaining 是派生字段（estimated_hours - COALESCE(spent_hours, 0)），不对应实际列，需特殊处理
                hasSpecialSort = true;
                if (desc) {
                    wrapper.last("ORDER BY (COALESCE(estimated_hours, 0) - COALESCE(spent_hours, 0)) DESC NULLS LAST");
                } else {
                    wrapper.last("ORDER BY (COALESCE(estimated_hours, 0) - COALESCE(spent_hours, 0)) ASC NULLS LAST");
                }
            } else if ("priority".equals(sortField)) {
                // 使用 IssuePriorityHelper 统一优先级排序逻辑
                // ASC = 优先级从高到低（紧急→低），DESC = 优先级从低到高（低→紧急）
                hasSpecialSort = true;
                if (desc) {
                    wrapper.last("ORDER BY " + IssuePriorityHelper.PRIORITY_ORDER_EXPR + " DESC, updated_at DESC");
                } else {
                    wrapper.last("ORDER BY " + IssuePriorityHelper.PRIORITY_ORDER_EXPR + " ASC, updated_at DESC");
                }
            }
        }

        // 默认排序兜底（当无有效自定义字段排序且 toPage() 也无有效排序时生效）
        if (!hasCustomFieldSort && !hasSpecialSort) {
            wrapper.orderByDesc("updated_at");
        }

        // 对于自定义字段排序或特殊字段排序，清空 sort 参数避免 toPage() 产生冲突的 ORDER BY
        if (hasCustomFieldSort || hasSpecialSort) {
            String originalSort = query.getSort();
            query.setSort(null);
            Page<Issue> result = issueMapper.selectPage(query.toPage(), wrapper);
            query.setSort(originalSort); // 恢复原值，避免影响调用方
            return result;
        }
        return issueMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 工单列表查询（返回分页结果）。
     * Controller 负责通过 Converter + Assembler 将结果转为 VO。
     */
    @Transactional(readOnly = true)
    public Page<Issue> listIssuesPage(IssueQuery query) {
        return listByQuery(query);
    }

    /**
     * 查找与给定关键词相似的工单（用于创建工单时的重复检测）。
     * 使用全文索引 + trigram 匹配搜索标题相似的工单，结果轻量只包含展示必要字段。
     *
     * @param keyword   搜索关键词（来自新工单的标题）
     * @param projectId 限定搜索的项目 ID（可选，为 null 时搜索用户有权访问的所有项目）
     * @param limit     最大返回数量
     * @return 相似工单列表
     */
    public List<Issue> findSimilarIssues(String keyword, Long projectId, int limit) {
        if (keyword == null || keyword.isBlank() || keyword.length() < 3) {
            return Collections.emptyList();
        }

        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        // 限定项目范围
        if (projectId != null) {
            wrapper.eq("project_id", projectId);
        } else {
            // 如果未指定项目，限制搜索范围为用户可访问的项目
            Long currentUserId = SecurityUtils.getCurrentUserId();
            List<Long> accessibleProjectIds = projectService.getAccessibleProjectIds(currentUserId);
            if (accessibleProjectIds != null) {
                if (accessibleProjectIds.isEmpty()) {
                    return Collections.emptyList();
                }
                wrapper.in("project_id", accessibleProjectIds);
            }
            // null 表示系统管理员，不加限制
        }

        // 复用关键词搜索逻辑
        applyKeywordFilter(wrapper, keyword);

        // 只取标题相关度最高的结果，按更新时间倒序
        wrapper.orderByDesc("updated_at");

        Page<Issue> page = new Page<>(1, limit);
        page.setSearchCount(false); // 不需要 count 查询，提高性能
        Page<Issue> result = issueMapper.selectPage(page, wrapper);

        return result.getRecords();
    }

    /**
     * 查询项目看板是否开启了 allowMultipleSprints 配置
     */
    private boolean isAllowMultipleSprints(Long projectId) {
        com.trackflow.board.entity.BoardGeneralConfig config = boardGeneralConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.trackflow.board.entity.BoardGeneralConfig>()
                        .eq(com.trackflow.board.entity.BoardGeneralConfig::getProjectId, projectId));
        return config != null && Boolean.TRUE.equals(config.getAllowMultipleSprints());
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
     * WIP 限制校验（优先级模式）：检查将工单移入目标优先级列是否超出 WIP 上限。
     * 使用 statusId=0 + fieldValue 组合查询 board_column_config。
     *
     * @param projectId       项目 ID
     * @param targetFieldValue 目标优先级值（Critical/High/Normal/Low）
     * @return 超限警告信息；null 表示不超限或目标列无 WIP 配置
     */
    public String checkWipLimitByFieldValue(Long projectId, String targetFieldValue) {
        com.trackflow.board.entity.BoardColumnConfig columnConfig = boardColumnConfigMapper.selectOne(
                new LambdaQueryWrapper<com.trackflow.board.entity.BoardColumnConfig>()
                        .eq(com.trackflow.board.entity.BoardColumnConfig::getProjectId, projectId)
                        .isNull(com.trackflow.board.entity.BoardColumnConfig::getStatusId)
                        .eq(com.trackflow.board.entity.BoardColumnConfig::getFieldValue, targetFieldValue));
        if (columnConfig == null || columnConfig.getWipMax() == null) return null;

        long currentCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getPriority, targetFieldValue));
        if (currentCount >= columnConfig.getWipMax()) {
            return String.format("目标优先级列「%s」已达到 WIP 上限（%d/%d），确定要继续移入吗？",
                    targetFieldValue, currentCount, columnConfig.getWipMax());
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
     * 状态转换前置校验（WIP 限制 + 关闭前置检查 + 描述为空检查）。
     * Controller 调用此方法获取警告信息，由 Controller 决定返回给前端让用户确认。
     *
     * @return 警告信息列表（空表示无需确认，可直接转换）
     */
    public record TransitPreCheckResult(String wipWarning, String closeWarning, String descEmptyWarning) {
        public boolean hasWarnings() {
            return wipWarning != null || closeWarning != null || descEmptyWarning != null;
        }
    }

    /**
     * Testing 相关状态名称集合（用于描述为空警告检查）
     */
    private static final Set<String> TESTING_STATUS_NAMES = Set.of("Testing", "测试中");

    public TransitPreCheckResult checkTransitPreConditions(Issue issue, Long targetStatusId,
                                                           boolean forceWip, boolean forceClose,
                                                           boolean forceDescEmpty) {
        // WIP 限制校验
        String wipWarning = null;
        if (!forceWip) {
            wipWarning = checkWipLimit(issue.getProjectId(), targetStatusId);
        }

        // 关闭状态前置检查
        String closeWarning = null;
        IssueStatus targetStatus = statusMapper.selectById(targetStatusId);
        if (!forceClose) {
            if (targetStatus != null && targetStatus.getIsClosed()) {
                List<String> warnings = closePreCheckChain.execute(issue);
                if (!warnings.isEmpty()) {
                    closeWarning = "此工单" + String.join("，且", warnings) + "，确定要强制关闭吗？";
                }
            }
        }

        // 描述为空检查 — 转换到 Testing 状态时，如果描述为空，给出警告
        String descEmptyWarning = null;
        if (!forceDescEmpty && targetStatus != null) {
            if (TESTING_STATUS_NAMES.contains(targetStatus.getName())) {
                if (issue.getDescription() == null || issue.getDescription().isBlank()) {
                    descEmptyWarning = "工单描述为空，测试人员将无法确认验收内容。是否仍要变更状态为 " + targetStatus.getLocalizedName() + "？";
                }
            }
        }

        return new TransitPreCheckResult(wipWarning, closeWarning, descEmptyWarning);
    }

    /**
     * 兼容旧调用（不检查描述为空）
     */
    public TransitPreCheckResult checkTransitPreConditions(Issue issue, Long targetStatusId,
                                                           boolean forceWip, boolean forceClose) {
        return checkTransitPreConditions(issue, targetStatusId, forceWip, forceClose, false);
    }

    /**
     * 获取指定工单的可用状态转换列表（已附加转换名、强制评论标记、阻塞信息）。
     * 封装了 Controller 中的 enrichment 逻辑，确保 Controller 只做编排。
     *
     * @param issueId 工单 ID
     * @return 已标注完整信息的可用状态 VO 列表
     */
    @Transactional(readOnly = true)
    public AvailableTransitionsResult getAvailableTransitionsData(Long issueId) {
        Issue issue = getByIdWithAccessCheck(issueId);
        Long userId = SecurityUtils.getCurrentUserId();

        // 查询可用转换列表和转换显示名
        WorkflowService.TransitionResult result = workflowService.getAvailableTransitionsWithNames(issue, userId);
        List<IssueStatus> statuses = result.statuses();
        Map<Long, String> transitionNames = result.transitionNames();

        // 查询需要强制评论的状态 ID 集合
        List<Long> targetStatusIds = statuses.stream().map(IssueStatus::getId).toList();
        Set<Long> requireCommentIds = workflowService.getRequireCommentStatusIds(
                issue.getStatusId(), targetStatusIds);

        // 查询未解决 blocker
        List<String> blockerKeys = issueLinkService.getUnresolvedBlockerKeys(issueId);

        return new AvailableTransitionsResult(statuses, transitionNames, requireCommentIds, blockerKeys);
    }

    /**
     * 可用状态转换查询结果（不含 VO）
     */
    public record AvailableTransitionsResult(
            List<IssueStatus> statuses,
            Map<Long, String> transitionNames,
            Set<Long> requireCommentStatusIds,
            List<String> blockerKeys
    ) {}

    /**
     * 执行状态转换完整业务流程（含所有前置校验）。
     * 封装了工作流权限校验、强制评论校验、WIP/关闭/描述前置检查和最终状态变更。
     *
     * @param id  工单 ID
     * @param dto 状态转换请求参数
     * @return 包含更新后版本号和动作执行结果
     * @throws BusinessException 当校验不通过时
     */
    public TransitStatusResultVO performTransition(Long id, TransitStatusDTO dto) {
        Issue issue = getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();

        // 工作流权限校验
        if (!workflowService.isTransitionAllowed(issue, dto.getStatusId(), userId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_DENIED, "当前角色不允许执行此状态转换");
        }

        // 强制评论校验
        if (workflowService.isCommentRequired(issue.getStatusId(), dto.getStatusId())) {
            if (dto.getComment() == null || dto.getComment().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "此状态转换需要填写理由");
            }
        }

        // WIP 限制 + 关闭前置检查 + 描述为空检查
        TransitPreCheckResult preCheck = checkTransitPreConditions(
                issue, dto.getStatusId(),
                Boolean.TRUE.equals(dto.getForceWip()),
                Boolean.TRUE.equals(dto.getForce()),
                Boolean.TRUE.equals(dto.getForceDescEmpty()));

        if (preCheck.wipWarning() != null) {
            throw new BusinessException(ErrorCode.WIP_LIMIT_EXCEEDED, preCheck.wipWarning());
        }
        if (preCheck.closeWarning() != null) {
            throw new BusinessException(ErrorCode.CLOSE_CONFIRMATION_REQUIRED, preCheck.closeWarning());
        }
        if (preCheck.descEmptyWarning() != null) {
            throw new BusinessException(ErrorCode.DESCRIPTION_EMPTY_WARNING, preCheck.descEmptyWarning());
        }

        // 执行状态变更（skipWorkflowCheck=true，因为此方法已完成工作流校验）
        ActionExecutionResult actionResult = transitStatus(id, dto.getStatusId(), dto.getComment(),
                dto.getAssigneeId(), Boolean.TRUE.equals(dto.getAssigneeExplicit()),
                dto.getVersion(), true);

        // 字段校验失败时，返回校验结果但不更新版本号
        if (actionResult != null && actionResult.getOutcome() == ActionExecutionResult.Outcome.FIELD_VALIDATION_FAILED) {
            return TransitStatusResult.of(dto.getVersion(), actionResult);
        }

        // 返回更新后的版本号 + 动作执行结果
        Issue updated = getById(id);
        return TransitStatusResult.of(updated.getVersion(), actionResult);
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
     * 撤销状态变更 — 完整业务流程：
     * 1. 查询最后一次状态变更记录
     * 2. 时间窗口校验（30秒内有效）
     * 3. 操作人校验（只能撤销自己的操作）
     * 4. 并发修改检测（工单未被他人修改）
     * 5. 目标状态验证（必须回退到上一个状态）
     * 6. 执行撤销（跳过工作流校验）
     *
     * @param issueId        工单 ID
     * @param targetStatusId 目标状态 ID（应为上一次变更前的状态）
     * @return 包含更新后版本号和动作执行结果
     * @throws BusinessException 当校验不通过时
     */
    @Transactional(rollbackFor = Exception.class)
    public TransitStatusResultVO undoTransitStatus(Long issueId, Long targetStatusId) {
        IssueActivity lastStatusChange = getLastStatusChange(issueId);
        if (lastStatusChange == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该工单没有状态变更记录，无法撤销");
        }

        // 时间窗口校验：撤销仅在 30 秒内有效
        long elapsedSeconds = Duration.between(lastStatusChange.getCreatedAt(), LocalDateTime.now()).getSeconds();
        if (elapsedSeconds > 30) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "撤销窗口已过期（30秒内有效）");
        }

        // 操作人校验：只能撤销自己的操作
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!currentUserId.equals(lastStatusChange.getUserId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能撤销自己的操作");
        }

        // 并发修改检测：检查该工单在状态变更之后是否有其他人做过修改
        Issue currentIssue = getById(issueId);
        if (currentIssue.getUpdatedBy() != null && !currentUserId.equals(currentIssue.getUpdatedBy())) {
            if (currentIssue.getUpdatedAt() != null && currentIssue.getUpdatedAt().isAfter(lastStatusChange.getCreatedAt())) {
                throw new BusinessException(ErrorCode.CONFLICT, "工单已被其他人修改，无法撤销");
            }
        }

        // 验证目标状态与上一次变更的旧状态一致
        validateUndoTargetStatus(targetStatusId, lastStatusChange);

        // 执行撤销（跳过工作流校验，目标状态已验证为上一状态）— 不传 comment，撤销不应产生普通评论
        ActionExecutionResult actionResult = transitStatusSkipWorkflow(issueId, targetStatusId, null);

        // 返回更新后的版本号
        Issue updated = getById(issueId);
        return TransitStatusResult.of(updated.getVersion(), actionResult);
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
        // "none" means IS NOT NULL (e.g. assigneeIdNot=none → issues with assignee)
        if ("none".equalsIgnoreCase(value.trim())) {
            wrapper.isNotNull(column);
            return;
        }
        List<?> values = isNumeric
                ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList()
                : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        wrapper.notIn(column, values);
    }

    /**
     * 关键词过滤：使用混合搜索策略兼顾中英文和性能。
     * <p>
     * 搜索策略（OR 组合，确保覆盖范围不缩小）：
     * <ul>
     *   <li>全文搜索 (tsvector @@ plainto_tsquery) — 利用 idx_issue_fulltext GIN 索引，对空格分隔的英文词效果最佳</li>
     *   <li>title ILIKE — 利用 idx_issue_title_trgm trigram GIN 索引，对中文子串匹配有效</li>
     *   <li>description ILIKE — 利用 idx_issue_description_trgm trigram GIN 索引，匹配描述中的关键词</li>
     *   <li>issue_key ILIKE — 精确匹配工单编号</li>
     *   <li>assignee 名称 ILIKE — 利用 idx_sys_user_display_name_trgm 索引</li>
     * </ul>
     */
    private void applyKeywordFilter(QueryWrapper<Issue> wrapper, String keyword) {
        String escaped = SqlUtils.escapeLikePattern(keyword);
        String likePattern = "%" + escaped + "%";
        wrapper.and(w -> w
                // 全文搜索：利用 idx_issue_fulltext GIN 索引（对空格分隔的英文词效果最佳）
                .apply("to_tsvector('simple', COALESCE(title,'') || ' ' || COALESCE(description,'')) @@ plainto_tsquery('simple', {0})", keyword)
                .or()
                // title 子串匹配：利用 idx_issue_title_trgm trigram GIN 索引（对中文子串有效）
                .apply("title ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                // description 子串匹配：利用 idx_issue_description_trgm trigram GIN 索引
                .apply("description ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                // issue_key 匹配
                .apply("issue_key ILIKE {0} ESCAPE '\\'", likePattern)
                .or()
                // assignee 名称匹配：利用 idx_sys_user_display_name_trgm / idx_sys_user_username_trgm
                .apply("assignee_id IN (SELECT id FROM sys_user WHERE display_name ILIKE {0} ESCAPE '\\' OR username ILIKE {0} ESCAPE '\\')", likePattern)
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
        List<String> warnings = new java.util.ArrayList<>();

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
        if (dto.getIssueType() != null) {
            String oldType = issue.getIssueType();
            String newType = issueTypeFieldService.normalizeIssueType(dto.getIssueType(), issue.getProjectId());

            // 归一化后实际未变更则跳过
            if (!newType.equals(oldType)) {
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
        }
        if (dto.getPriority() != null) {
            String oldPriority = issue.getPriority();
            // WIP 限制校验（优先级模式看板）：仅在优先级实际变更且未强制跳过时检查
            if (!dto.getPriority().equals(oldPriority) && !Boolean.TRUE.equals(dto.getForceWip())) {
                String wipWarning = checkWipLimitByFieldValue(issue.getProjectId(), dto.getPriority());
                if (wipWarning != null) {
                    throw new BusinessException(ErrorCode.WIP_LIMIT_EXCEEDED, wipWarning);
                }
            }
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
                // 权限不足时跳过 Sprint 字段并收集警告（不阻塞其他合法字段更新）
                warnings.add("Sprint 修改被跳过：需要 sprint:edit 权限");
                log.info("Issue {} sprint update skipped: user {} lacks sprint:edit permission on project {}",
                        id, currentUserId, issue.getProjectId());
            } else {
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
                Long newPrimarySprintId = dto.getSprintId() == 0 ? null : dto.getSprintId();
                issue.setSprintId(newPrimarySprintId);
                // 同步 issue_sprint 关联表：覆盖模式下替换所有关联
                issueSprintMapper.deleteByIssueId(id);
                if (newPrimarySprintId != null) {
                    IssueSprint relation = new IssueSprint();
                    relation.setIssueId(id);
                    relation.setSprintId(newPrimarySprintId);
                    relation.setCreatedAt(java.time.LocalDateTime.now());
                    issueSprintMapper.insert(relation);
                }
                // 收集迭代变更
                fieldChanges.put("sprint", new String[]{oldSprintName, newSprintName});
            }
        }
        // 追加 Sprint（多 Sprint 模式）
        if (dto.getAddToSprintId() != null) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "sprint:edit")) {
                warnings.add("Sprint 追加被跳过：需要 sprint:edit 权限");
            } else {
                // 检查项目是否开启了 allowMultipleSprints
                boolean allowMultiple = isAllowMultipleSprints(issue.getProjectId());
                if (!allowMultiple) {
                    warnings.add("Sprint 追加被跳过：项目看板未开启 allowMultipleSprints 配置");
                } else {
                    Long addSprintId = dto.getAddToSprintId();
                    // 校验目标 Sprint 存在且属于同一项目
                    var targetSprint = sprintMapper.selectById(addSprintId);
                    if (targetSprint == null || !targetSprint.getProjectId().equals(issue.getProjectId())) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "目标 Sprint 不存在或不属于当前项目");
                    }
                    // 检查是否已关联
                    List<Long> existingSprintIds = issueSprintMapper.selectSprintIdsByIssueId(id);
                    if (!existingSprintIds.contains(addSprintId)) {
                        IssueSprint relation = new IssueSprint();
                        relation.setIssueId(id);
                        relation.setSprintId(addSprintId);
                        relation.setCreatedAt(java.time.LocalDateTime.now());
                        issueSprintMapper.insert(relation);
                        // 如果主 Sprint 为空，同时设为主 Sprint
                        if (issue.getSprintId() == null) {
                            issue.setSprintId(addSprintId);
                        }
                        recordActivity(id, currentUserId, "updated", "sprint_added", null, String.valueOf(addSprintId), null, targetSprint.getName());
                        fieldChanges.put("sprint_added", new String[]{null, targetSprint.getName()});
                    }
                }
            }
        }
        // 移除 Sprint 关联（多 Sprint 模式）
        if (dto.getRemoveFromSprintId() != null) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "sprint:edit")) {
                warnings.add("Sprint 移除被跳过：需要 sprint:edit 权限");
            } else {
                Long removeSprintId = dto.getRemoveFromSprintId();
                var removedSprint = sprintMapper.selectById(removeSprintId);
                String removedSprintName = removedSprint != null ? removedSprint.getName() : null;
                issueSprintMapper.deleteByIssueIdAndSprintId(id, removeSprintId);
                // 如果移除的是主 Sprint，重新选择一个作为主 Sprint
                if (removeSprintId.equals(issue.getSprintId())) {
                    List<Long> remainingSprintIds = issueSprintMapper.selectSprintIdsByIssueId(id);
                    issue.setSprintId(remainingSprintIds.isEmpty() ? null : remainingSprintIds.get(0));
                }
                recordActivity(id, currentUserId, "updated", "sprint_removed", String.valueOf(removeSprintId), null, removedSprintName, null);
                fieldChanges.put("sprint_removed", new String[]{removedSprintName, null});
            }
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
            // 必填校验：不允许清空必填字段
            if (dueDateFieldService.isRequiredForProject(issue.getProjectId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "截止日期为必填字段，不允许清空");
            }
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
        // 可见性字段更新：只有项目管理员或工单报告者有权修改
        if (dto.getVisibility() != null) {
            boolean canChangeVisibility = issue.getReporterId().equals(currentUserId)
                    || permissionService.hasPermission(currentUserId, issue.getProjectId(), "project:admin")
                    || permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_visibility");
            if (!canChangeVisibility) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权修改工单可见性，仅报告者或项目管理员可修改");
            }
            String newVisibility = dto.getVisibility();
            if (!newVisibility.equals("public") && !newVisibility.equals("restricted")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的可见性值：" + newVisibility + "，有效值为 public/restricted");
            }
            String oldVisibility = issue.getVisibility() != null ? issue.getVisibility() : "public";
            if (!newVisibility.equals(oldVisibility)) {
                recordActivity(id, currentUserId, "updated", "visibility", oldVisibility, newVisibility);
                issue.setVisibility(newVisibility);
                fieldChanges.put("visibility", new String[]{oldVisibility, newVisibility});
            }
            // 更新可见用户列表（仅 restricted 时有意义）
            if ("restricted".equals(newVisibility)) {
                visibilityUserMapper.deleteByIssueId(id);
                List<Long> userIds = dto.getVisibilityUserIds();
                if (userIds != null && !userIds.isEmpty()) {
                    for (Long userId : userIds) {
                        com.trackflow.issue.entity.IssueVisibilityUser vu = new com.trackflow.issue.entity.IssueVisibilityUser();
                        vu.setIssueId(id);
                        vu.setUserId(userId);
                        visibilityUserMapper.insert(vu);
                    }
                }
            } else {
                // 切回 public 时清空访问列表
                visibilityUserMapper.deleteByIssueId(id);
            }
        }

        int rows = issueMapper.updateById(issue);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已被其他人修改，请刷新页面后重试");
        }

        // 触发 on-field-changed 自动化规则（通过事件，解耦）
        fireFieldChangeRules(issue, dto, oldValues);

        // 发布合并的字段变更通知事件（事务提交后触发，一次操作只产生一条通知）
        if (!fieldChanges.isEmpty()) {
            eventPublisher.publishEvent(new IssueNotificationEvent.MultiFieldUpdated(
                    issue, fieldChanges, currentUserId, null));
        }

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "issue_updated"));

        return new UpdateResult(issue, statusAutoReset, warnings);
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

        int rows = issueMapper.updateById(issue);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已被其他人修改，请刷新页面后重试");
        }

        // 自定义字段清理：移除不适用于目标项目的字段值
        customFieldService.removeOrphanValues(issueId, issue.getIssueType(), targetProjectId);

        // 子工单反向清理：清除源项目中仍引用此工单的子工单的 parent_id
        List<Issue> orphanChildren = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getParentId, issueId)
                        .isNull(Issue::getDeletedAt));
        if (!orphanChildren.isEmpty()) {
            issueMapper.clearParentId(issueId);
            for (Issue child : orphanChildren) {
                recordActivity(child.getId(), currentUserId, "updated", "parent",
                        oldIssueKey, null, oldIssueKey, null);
            }
            // 刷新被移动工单的 childCount/childClosedCount（子工单已解除，归零）
            ancestorRefreshService.refreshAncestorChain(issueId);
            log.info("Issue {} moved: cleared parent_id on {} child issues in source project",
                    issueId, orphanChildren.size());
        }

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

    /**
     * 自动化规则使用的内部移动方法——跳过权限检查，使用指定的 operatorId 作为操作者。
     * 用于 WorkflowRuleEngine 的 move_to_project 动作。
     *
     * @param issueId         要移动的工单 ID
     * @param targetProjectId 目标项目 ID
     * @param operatorId      操作者 ID（规则创建者）
     * @return 移动后的 Issue 实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Issue moveToProjectByAutomation(Long issueId, Long targetProjectId, Long operatorId) {
        Issue issue = getById(issueId);
        Long sourceProjectId = issue.getProjectId();

        if (sourceProjectId.equals(targetProjectId)) {
            log.warn("[Automation] moveToProject: issue {} already in target project {}", issueId, targetProjectId);
            return issue;
        }

        var sourceProject = projectService.getById(sourceProjectId);
        var targetProject = projectService.getById(targetProjectId);
        if (targetProject == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标项目不存在: " + targetProjectId);
        }

        // 生成新的 issue_key
        String oldIssueKey = issue.getIssueKey();
        int newSeq = projectService.nextIssueSequence(targetProjectId);
        String newIssueKey = targetProject.getKey() + "-" + newSeq;

        // Sprint 置空
        Long oldSprintId = issue.getSprintId();
        issue.setSprintId(null);

        // Assignee 清理：如果不在目标项目
        Long oldAssigneeId = issue.getAssigneeId();
        if (oldAssigneeId != null && !projectService.isProjectMember(oldAssigneeId, targetProjectId)) {
            issue.setAssigneeId(null);
        }

        // 更新核心字段
        issue.setProjectId(targetProjectId);
        issue.setIssueKey(newIssueKey);

        // 子工单处理
        if (issue.getParentId() != null) {
            Issue parent = issueMapper.selectById(issue.getParentId());
            if (parent == null || !parent.getProjectId().equals(targetProjectId)) {
                issue.setParentId(null);
            }
        }

        // 保存旧 Key 到历史表
        IssueKeyHistory keyHistory = new IssueKeyHistory();
        keyHistory.setIssueId(issueId);
        keyHistory.setOldKey(oldIssueKey);
        keyHistory.setNewKey(newIssueKey);
        keyHistory.setChangedBy(operatorId);
        issueKeyHistoryMapper.insert(keyHistory);

        // 状态兼容性检查
        boolean statusValid = workflowService.isStatusInWorkflow(
                targetProjectId, issue.getIssueType(), issue.getStatusId());
        if (!statusValid) {
            var defaultStatus = workflowService.getDefaultStatus();
            if (defaultStatus != null) {
                Long oldStatusId = issue.getStatusId();
                issue.setStatusId(defaultStatus.getId());
                String oldStatusName = statusCacheHelper.getStatusName(oldStatusId);
                String newStatusName = defaultStatus.getLocalizedName();
                recordActivity(issueId, operatorId, "status_reset", "status",
                        oldStatusName, newStatusName);
            }
        }

        issueMapper.updateById(issue);

        // 自定义字段清理
        customFieldService.removeOrphanValues(issueId, issue.getIssueType(), targetProjectId);

        // 子工单反向清理
        List<Issue> orphanChildren = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getParentId, issueId)
                        .isNull(Issue::getDeletedAt));
        if (!orphanChildren.isEmpty()) {
            issueMapper.clearParentId(issueId);
            for (Issue child : orphanChildren) {
                recordActivity(child.getId(), operatorId, "updated", "parent",
                        oldIssueKey, null, oldIssueKey, null);
            }
            ancestorRefreshService.refreshAncestorChain(issueId);
        }

        // 更新 time_entry 的 project_id
        timeEntryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.trackflow.timeentry.entity.TimeEntry>()
                        .eq(com.trackflow.timeentry.entity.TimeEntry::getIssueId, issueId)
                        .set(com.trackflow.timeentry.entity.TimeEntry::getProjectId, targetProjectId));

        // 活动记录
        String sourceProjectName = sourceProject != null ? sourceProject.getName() : String.valueOf(sourceProjectId);
        String targetProjectName = targetProject.getName();
        recordActivity(issueId, operatorId, "moved_to_project", "project",
                sourceProjectName, targetProjectName);

        if (oldSprintId != null) {
            var oldSprint = sprintMapper.selectById(oldSprintId);
            String oldSprintName = oldSprint != null ? oldSprint.getName() : null;
            recordActivity(issueId, operatorId, "updated", "sprint",
                    String.valueOf(oldSprintId), null, oldSprintName, null);
        }

        if (oldAssigneeId != null && issue.getAssigneeId() == null) {
            String oldAssigneeName = getUserDisplayName(oldAssigneeId);
            recordActivity(issueId, operatorId, "assigned", "assignee", oldAssigneeName, null);
        }

        recordActivity(issueId, operatorId, "updated", "issue_key", oldIssueKey, newIssueKey);

        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sourceProjectId, "issue_moved"));
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(targetProjectId, "issue_moved"));

        log.info("[Automation] Issue {} moved from project {} to project {}. Key: {} → {}",
                issueId, sourceProjectId, targetProjectId, oldIssueKey, newIssueKey);

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
    public List<ChildIssueRow> listChildrenRows(Long parentId) {
        return issueMapper.selectChildrenByParentId(parentId);
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
     * 部分成功部分失败是设计意图（非事务），结果收集在 BatchOperationResult 中。
     *
     * @param issueIds       工单 ID 列表
     * @param permissionCode 权限代码（如 "issue:edit"）
     * @param action         对单个 issue 执行的业务动作（入参为已通过校验的 issue），返回 null 表示成功，返回非空字符串表示业务拒绝原因
     * @param operationName  操作名称（用于日志）
     */
    private BatchOperationResult executeBatch(
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
    private BatchOperationResult executeBatchWithSilent(
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
    private BatchOperationResult doExecuteBatch(
            List<Long> issueIds,
            String permissionCode,
            BatchIssueAction action,
            String operationName) {

        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
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
     * 批量状态转换（带乐观锁 + 备注支持）。
     *
     * @param issueIds 要转换的工单 ID 列表
     * @param statusId 目标状态 ID
     * @param comment  可选备注（记录到活动日志）
     * @param versions 乐观锁版本映射（issueId → version），为 null 时跳过版本校验
     * @param silent   静默模式，为 true 时不发送通知
     */
    public BatchOperationResult batchTransitStatus(List<Long> issueIds, Long statusId,
                                                     String comment, Map<Long, Integer> versions, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:change_status", (issue, userId) -> {
            if (!workflowService.isTransitionAllowed(issue, statusId, userId)) {
                return "工作流不允许此状态转换";
            }
            Integer expectedVersion = versions != null ? versions.get(issue.getId()) : null;
            ActionExecutionResult transitResult = transitStatus(
                    issue.getId(), statusId, comment, null, false, expectedVersion, true);
            if (transitResult != null
                    && transitResult.getOutcome() == ActionExecutionResult.Outcome.FIELD_VALIDATION_FAILED) {
                String warningMessage = transitResult.getWarningMessage();
                return warningMessage != null && !warningMessage.isBlank()
                        ? warningMessage
                        : "字段校验失败，无法完成状态转换";
            }
            return null;
        }, "状态转换", silent);
    }

    /**
     * 批量分配
     */
    public BatchOperationResult batchAssign(List<Long> issueIds, Long assigneeId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:assign", (issue, userId) -> {
            assign(issue.getId(), assigneeId);
            return null;
        }, "分配", silent);
    }

    /**
     * 批量更新 Sprint
     */
    public BatchOperationResult batchUpdateSprint(List<Long> issueIds, Long sprintId, boolean silent) {
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
    public BatchOperationResult batchUpdatePriority(List<Long> issueIds, String priority, boolean silent) {
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
    public BatchOperationResult batchAddTag(List<Long> issueIds, Long tagId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            tagService.addTagToIssue(issue.getId(), tagId);
            return null;
        }, "添加标签", silent);
    }

    /**
     * 批量移除标签
     */
    public BatchOperationResult batchRemoveTag(List<Long> issueIds, Long tagId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            tagService.removeTagFromIssue(issue.getId(), tagId);
            return null;
        }, "移除标签", silent);
    }

    /**
     * 批量删除
     */
    public BatchOperationResult batchDelete(List<Long> issueIds) {
        return executeBatch(issueIds, "issue:delete", (issue, userId) -> {
            delete(issue.getId());
            return null;
        }, "删除");
    }

    /**
     * 批量恢复（从回收站还原）
     * 注意：此方法不使用 executeBatch 模板，因为已删除工单 getById() 会抛异常
     */
    public BatchOperationResult batchRestore(List<Long> issueIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
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
                // 失效 Dashboard + 看板缓存（恢复的工单重新出现在列统计中）
                eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(projectId, "issue_restored"));
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
        return transitStatus(id, newStatusId, comment, null, false, null, true, true);
    }

    /**
     * 状态变更内部实现
     *
     * @param skipWorkflowCheck true = 跳过工作流规则校验（Controller 已预校验或撤销操作）
     * @return 动作执行结果摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet,
                              Integer expectedVersion, boolean skipWorkflowCheck) {
        return transitStatus(id, newStatusId, comment, assigneeId, assigneeExplicitlySet, expectedVersion, skipWorkflowCheck, false);
    }

    /**
     * 状态变更内部实现（完整参数版本）
     *
     * @param skipWorkflowCheck true = 跳过工作流规则校验（Controller 已预校验或撤销操作）
     * @param isUndo            true = 撤销操作，活动记录使用 status_reverted action
     * @return 动作执行结果摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult transitStatus(Long id, Long newStatusId, String comment,
                              Long assigneeId, boolean assigneeExplicitlySet,
                              Integer expectedVersion, boolean skipWorkflowCheck, boolean isUndo) {
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

        // 前置字段校验（require_field 动作）
        // 如果必填字段为空，返回校验失败结果，不执行状态变更
        ActionExecutionResult preValidation = transitionActionEngine.validatePreTransition(
                issue, oldStatusId, newStatusId);
        if (preValidation != null && preValidation.getOutcome() == ActionExecutionResult.Outcome.FIELD_VALIDATION_FAILED) {
            log.info("[IssueService] Issue {} 状态转换被阻止：字段校验失败，field={}, message={}",
                    issue.getId(), preValidation.getRequiredFieldName(), preValidation.getWarningMessage());
            return preValidation;
        }

        issue.setStatusId(newStatusId);
        if (newStatus.getIsClosed()) {
            issue.setResolvedAt(LocalDateTime.now());
        } else {
            issue.setResolvedAt(null);
        }
        int rows = issueMapper.updateById(issue);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已被其他人修改，请刷新页面后重试");
        }

        // 记录状态变更活动（撤销操作使用 status_reverted，普通变更使用 status_changed）
        IssueStatus oldStatus = statusMapper.selectById(oldStatusId);
        String oldStatusDisplayName = oldStatus != null ? oldStatus.getLocalizedName() : String.valueOf(oldStatusId);
        String newStatusDisplayName = newStatus.getLocalizedName();
        String activityAction = isUndo ? "status_reverted" : "status_changed";
        Long statusActivityId = recordActivity(id, currentUserId, activityAction, "status",
                oldStatusDisplayName, newStatusDisplayName);

        // 通知报告人+负责人状态已变更 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.StatusChanged(issue, oldStatusId, newStatusId, currentUserId, statusActivityId));

        // 触发 field_changed 自动化规则（status_id 字段）— 对标 YouTrack On-change 规则可监听 State 字段
        eventPublisher.publishEvent(new WorkflowRuleEvent.FieldChanged(
                issue.getId(), issue.getProjectId(), "status_id", String.valueOf(oldStatusId)));

        // 触发 issue_resolved / issue_unresolved 规则 — 对标 YouTrack "Issue Becomes Resolved/Unresolved"
        boolean oldIsClosed = oldStatus != null && Boolean.TRUE.equals(oldStatus.getIsClosed());
        boolean newIsClosed = Boolean.TRUE.equals(newStatus.getIsClosed());
        if (!oldIsClosed && newIsClosed) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.IssueResolved(issue.getId(), issue.getProjectId()));
        } else if (oldIsClosed && !newIsClosed) {
            eventPublisher.publishEvent(new WorkflowRuleEvent.IssueUnresolved(issue.getId(), issue.getProjectId()));
        }

        // 如果是转换到 cancelled 类别，额外发布取消事件（供外部集成模块监听）
        if (IssueStatusCategory.CANCELLED.getValue().equals(newStatus.getCategory())) {
            eventPublisher.publishEvent(new IssueNotificationEvent.Cancelled(issue, currentUserId));
        }

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "issue_status_changed"));

        // 如果带了评论，同时添加评论
        if (comment != null && !comment.isBlank()) {
            commentService.addComment(id, comment);
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
        int rows = issueMapper.updateById(issue);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已被其他人修改，请刷新页面后重试");
        }

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

        // 权限校验：设置评论可见性需要 issue:edit 权限
        // 无权用户静默忽略 visibleToGroupIds 字段
        if (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:edit")) {
                visibleToGroupIds = null;
            }
        }

        IssueComment comment = new IssueComment();
        comment.setIssueId(issueId);
        comment.setUserId(currentUserId);
        comment.setContent(content);
        comment.setSource(AutomationActorRunner.isAutomationExecution() ? "automation" : "web");
        // 设置可见性：空列表视为 null（全体可见）
        if (visibleToGroupIds != null && !visibleToGroupIds.isEmpty()) {
            comment.setVisibleToGroupIds(visibleToGroupIds);
        }
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        recordActivity(issueId, currentUserId, "commented", null, null, null);

        // 通知报告人+负责人+之前评论者 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Commented(issue, currentUserId, comment.getId()));

        // 解析评论中的 @mention 并通知被提及的用户 — 事务提交后触发
        eventPublisher.publishEvent(new IssueNotificationEvent.Mentioned(issue, content, currentUserId, comment.getId()));

        // 触发 comment_added 工作流规则 — 事务提交后异步执行
        eventPublisher.publishEvent(new WorkflowRuleEvent.CommentAdded(
                issueId, issue.getProjectId(), comment.getId(), content));

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
            // 权限校验：设置评论可见性需要 issue:edit 权限
            // 无权用户静默忽略可见性修改
            if (permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:edit")) {
                // 空列表视为 null（移除限制）
                comment.setVisibleToGroupIds(
                        visibleToGroupIds != null && !visibleToGroupIds.isEmpty() ? visibleToGroupIds : null);
            }
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

        // 保存被删除评论内容（去 HTML 标签，截断至 500 字符）— 必须在 deleteById 之前获取
        String deletedContent = stripHtmlForActivity(comment.getContent());

        // 软删除：MyBatis-Plus 全局逻辑删除会将 deleteById 转为 UPDATE SET deleted_at = NOW()
        commentMapper.deleteById(commentId);

        // 写入活动记录（不设 fieldName，避免被前端当作字段变更渲染）
        recordActivity(issueId, currentUserId, "comment_deleted", null, deletedContent, null);
    }

    /**
     * 还原已软删除的评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreComment(Long issueId, Long commentId) {
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        // 使用自定义查询绕过 MyBatis-Plus 全局逻辑删除过滤
        IssueComment comment = commentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (comment.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评论未被删除，无需还原");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        // 权限校验：作者本人 OR 拥有 issue:manage_comments 权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!comment.getUserId().equals(currentUserId)) {
            if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权还原他人评论");
            }
        }

        // 使用自定义 SQL 将 deleted_at 置为 NULL（updateById 不会更新逻辑删除字段）
        commentMapper.restoreById(commentId);

        recordActivity(issueId, currentUserId, "comment_restored", null, null, null);
    }

    /**
     * 永久删除评论（物理删除，不可恢复）
     */
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDeleteComment(Long issueId, Long commentId) {
        Issue issue = getById(issueId);
        projectService.assertProjectActive(issue.getProjectId());

        // 使用自定义查询绕过 MyBatis-Plus 全局逻辑删除过滤
        IssueComment comment = commentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不存在");
        }
        if (!comment.getIssueId().equals(issueId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评论不属于该工单");
        }

        // 永久删除需要 issue:manage_comments 权限（比软删除更严格）
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!permissionService.hasPermission(currentUserId, issue.getProjectId(), "issue:manage_comments")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权永久删除评论，需要 manage_comments 权限");
        }

        // 使用自定义 SQL 执行真正的物理删除（deleteById 会被全局配置转为软删除）
        commentMapper.physicalDeleteById(commentId);

        recordActivity(issueId, currentUserId, "comment_permanently_deleted", null, null, null);
    }

    /**
     * 去除 HTML 标签并截断内容用于活动记录
     */
    private String stripHtmlForActivity(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // 去除 HTML 标签
        String text = html.replaceAll("<[^>]+>", "").trim();
        // 去除多余空白
        text = text.replaceAll("\\s+", " ");
        if (text.length() > 500) {
            return text.substring(0, 497) + "...";
        }
        return text;
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

    /**
     * 更新单个状态节点在工作流画布中的坐标位置
     */
    public void updateStatusPosition(Long statusId, Double canvasX, Double canvasY) {
        IssueStatus status = statusMapper.selectById(statusId);
        if (status == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "状态不存在: " + statusId);
        }
        IssueStatus update = new IssueStatus();
        update.setId(statusId);
        update.setCanvasX(canvasX);
        update.setCanvasY(canvasY);
        statusMapper.updateById(update);
    }

    /**
     * 批量更新状态节点在工作流画布中的坐标位置
     */
    public void batchUpdateStatusPositions(List<com.trackflow.issue.dto.BatchUpdateStatusPositionDTO.StatusPositionItem> positions) {
        for (var item : positions) {
            IssueStatus update = new IssueStatus();
            update.setId(item.getStatusId());
            update.setCanvasX(item.getCanvasX());
            update.setCanvasY(item.getCanvasY());
            statusMapper.updateById(update);
        }
    }

    // ========== 增强详情（性能优化：单次 JOIN 查询） ==========

    /**
     * 通过 ID 或 Issue Key 获取详情（自动识别格式）。
     * <p>
     * 判断规则：如果参数全部由数字组成则视为 ID，否则视为 Issue Key。
     * 与 YouTrack API 行为一致：单个端点同时接受 entity ID 和 human-readable ID。
     */
    @Transactional(readOnly = true)
    public IssueDetailRow getDetailRowByIdOrKey(String idOrKey) {
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ID 或 Issue Key 不能为空");
        }
        // 纯数字 → 按 ID 查询
        if (idOrKey.matches("\\d+")) {
            Long id = Long.parseLong(idOrKey);
            return getDetailRowWithAccessCheck(id);
        }
        // 否则按 Issue Key 查询
        Issue issue = getByKeyWithAccessCheck(idOrKey);
        return getDetailRowWithAccessCheck(issue.getId());
    }

    /**
     * 获取 Issue 详情数据行（带项目成员校验 + 可见性校验）
     */
    @Transactional(readOnly = true)
    public IssueDetailRow getDetailRowWithAccessCheck(Long id) {
        IssueDetailRow row = getDetailRow(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(currentUserId, row.getProjectId());
        // 可见性校验：受限工单只有有权限的用户才能访问
        assertVisibilityAccessible(id, row, currentUserId);
        return row;
    }

    /**
     * 校验当前用户是否有权访问受限工单。
     * <p>
     * 访问规则：
     * - visibility = public：所有项目成员均可访问（已由 assertProjectAccessible 保证）
     * - visibility = restricted：仅报告者、负责人、issue_visibility_user 表中列出的用户、项目管理员可访问
     */
    private void assertVisibilityAccessible(Long issueId, IssueDetailRow row, Long currentUserId) {
        String visibility = row.getVisibility() != null ? row.getVisibility() : "public";
        if (!"restricted".equals(visibility)) {
            return; // public 工单不做额外校验
        }
        // 报告者始终可访问
        if (row.getReporterId() != null && currentUserId.equals(row.getReporterId())) {
            return;
        }
        // 负责人始终可访问
        if (row.getAssigneeId() != null && currentUserId.equals(row.getAssigneeId())) {
            return;
        }
        // 项目管理员始终可访问
        if (permissionService.hasPermission(currentUserId, row.getProjectId(), "project:admin")) {
            return;
        }
        // 检查是否在可见用户列表中
        if (visibilityUserMapper.existsByIssueIdAndUserId(issueId, currentUserId)) {
            return;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
    }

    /**
     * 获取 Issue 详情数据行（Mapper JOIN 结果） —— 单次 SQL JOIN 替代 N+1 查询。
     * Controller 通过 IssueDetailVOAssembler 将结果转为 IssueDetailVO。
     */
    @Transactional(readOnly = true)
    public IssueDetailRow getDetailRow(Long id) {
        IssueDetailRow row = issueMapper.selectDetailById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        return row;
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
        Long attachActivityId = recordActivity(issueId, currentUserId, "attachment_added", "attachment", null, safeFileName);

        // 发布通知事件
        eventPublisher.publishEvent(new IssueNotificationEvent.AttachmentAdded(issue, safeFileName, currentUserId, attachActivityId));

        // 触发 attachment_added 工作流规则
        eventPublisher.publishEvent(new WorkflowRuleEvent.AttachmentAdded(issueId, issue.getProjectId(), attachment.getId()));

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

        // 触发 attachment_removed 工作流规则
        eventPublisher.publishEvent(new WorkflowRuleEvent.AttachmentRemoved(issueId, issue.getProjectId(), attachmentId));
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
    public Page<TrashRow> listTrashPage(Long projectId, int page, int pageSize) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(currentUserId, projectId);
        Page<TrashRow> p = new Page<>(page, pageSize);
        return issueMapper.selectTrashPage(p, projectId);
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

        // 清理 Key 变更历史（FK 为 NO ACTION，必须手动清理）
        issueKeyHistoryMapper.delete(
                new LambdaQueryWrapper<IssueKeyHistory>().eq(IssueKeyHistory::getIssueId, id)
        );

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

    private Long recordActivity(Long issueId, Long userId, String action,
                                String fieldName, String oldValue, String newValue) {
        return activityService.recordActivity(issueId, userId, action, fieldName, oldValue, newValue);
    }

    private Long recordActivity(Long issueId, Long userId, String action,
                                String fieldName, String oldValue, String newValue,
                                String oldDisplayValue, String newDisplayValue) {
        return activityService.recordActivity(issueId, userId, action, fieldName, oldValue, newValue, oldDisplayValue, newDisplayValue);
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


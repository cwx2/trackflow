package com.trackflow.timeentry.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupMapper;
import com.trackflow.system.mapper.UserGroupMemberMapper;
import com.trackflow.timeentry.converter.TimeEntryConverter;
import com.trackflow.timeentry.dto.CreateTimeEntryDTO;
import com.trackflow.timeentry.dto.StartTimerDTO;
import com.trackflow.timeentry.dto.StopTimerDTO;
import com.trackflow.timeentry.dto.TimeEntryQueryDO;
import com.trackflow.timeentry.dto.UpdateTimeEntryDTO;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.mapper.TimeEntryMapper;
import com.trackflow.timeentry.vo.GroupTimeSummaryVO;
import com.trackflow.timeentry.vo.ProjectTimeSummaryVO;
import com.trackflow.timeentry.vo.TimeEntryAttributeValueVO;
import com.trackflow.timeentry.vo.TimeEntryUserVO;
import com.trackflow.timeentry.vo.TimeEntryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private static final String PERM_TIME_LOG = "time:log";
    private static final String PERM_TIME_LOG_FOR_OTHERS = "time:log_for_others";
    private static final String PERM_TIME_EDIT_ALL = "time:edit_all";
    private static final String PERM_TIME_DELETE_ALL = "time:delete_all";

    /** 单条工时最大分钟数（24小时） */
    private static final int MAX_DURATION_MINUTES = 1440;
    /** 工作日期最大回溯天数 */
    private static final int MAX_PAST_DAYS = 365;

    private final TimeEntryMapper timeEntryMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueMapper issueMapper;
    private final SysUserMapper sysUserMapper;
    private final UserGroupMapper groupMapper;
    private final UserGroupMemberMapper groupMemberMapper;
    private final com.trackflow.issue.service.AncestorRefreshService ancestorRefreshService;
    private final com.trackflow.workitemattr.service.WorkItemAttributeService workItemAttributeService;
    private final com.trackflow.project.service.ProjectService projectService;
    private final PermissionService permissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeEntryConverter timeEntryConverter;

    /**
     * 创建工时记录
     * 权限校验：
     * - 当前用户必须拥有 time:log 权限
     * - 若 forUserId 指定了其他用户，还需要 time:log_for_others 权限，
     *   且目标用户必须是工单所属项目的成员
     *
     * @param currentUserId 当前登录用户 ID（操作执行人）
     * @param dto 创建参数（可含 forUserId 指定工时归属人）
     */
    @Transactional(rollbackFor = Exception.class)
    public TimeEntry create(Long currentUserId, CreateTimeEntryDTO dto) {
        // 1. 校验工单存在且未软删除
        Issue issue = getActiveIssueOrThrow(dto.getIssueId());

        // 2. 确定工时归属人（targetUserId）和操作执行人（loggedBy = currentUserId）
        Long targetUserId = resolveTargetUser(currentUserId, dto.getForUserId(), issue.getProjectId());

        // 3. 校验当前用户为项目成员且有 time:log 权限
        projectService.assertProjectMember(currentUserId, issue.getProjectId());
        assertTimeLogPermission(currentUserId, issue.getProjectId());

        // 4. 校验项目是否启用了时间追踪
        if (!projectService.isTimeTrackingEnabled(issue.getProjectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该项目未启用时间追踪功能");
        }

        // 5. 校验工作日期合理性
        LocalDate workDate = parseAndValidateWorkDate(dto.getWorkDate());

        // 6. 校验 startTime + duration 不溢出一天
        validateStartTimeDurationCombination(dto.getStartTime(), dto.getDuration());

        TimeEntry entry = new TimeEntry();
        entry.setIssueId(dto.getIssueId());
        entry.setProjectId(issue.getProjectId());
        entry.setUserId(targetUserId);
        entry.setLoggedBy(currentUserId);
        entry.setWorkDate(workDate);
        entry.setDuration(dto.getDuration());
        entry.setStartTime(dto.getStartTime());
        entry.setDescription(dto.getDescription());
        entry.setOngoing(false);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        timeEntryMapper.insert(entry);

        // 保存工作项属性值（Work type 也通过此系统传递）
        if (dto.getAttributeValues() != null && !dto.getAttributeValues().isEmpty()) {
            Map<Long, Long> attrValueMap = new HashMap<>();
            for (Map.Entry<String, String> av : dto.getAttributeValues().entrySet()) {
                attrValueMap.put(Long.parseLong(av.getKey()), Long.parseLong(av.getValue()));
            }
            workItemAttributeService.saveTimeEntryAttributeValues(entry.getId(), attrValueMap);
        }

        // 记录活动：花费了 X 时间（结构化 JSON detail，供前端多列展示）
        String durationStr = formatDuration(dto.getDuration());
        String workTypeName = resolveWorkTypeName(dto.getAttributeValues());
        // new_value 保留为可读字符串（向下兼容）
        String newValueStr = workTypeName != null ? durationStr + " | " + workTypeName : durationStr;
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            newValueStr += " | " + dto.getDescription();
        }
        if (!targetUserId.equals(currentUserId)) {
            String loggerName = getUserDisplayName(currentUserId);
            newValueStr += " (由 " + loggerName + " 代录)";
        }
        // 构建结构化 detail JSON
        String timeEntryDetail = buildTimeEntryDetail(entry.getId(), workDate, dto.getDuration(),
                workTypeName, dto.getDescription());
        recordActivityWithDetail(dto.getIssueId(), targetUserId, "time_logged", "spent_time",
                null, newValueStr, timeEntryDetail);

        // 同步更新 issue.spent_hours + updated_at
        refreshIssueSpentHours(dto.getIssueId(), currentUserId);

        // 向上刷新父工单的派生属性
        ancestorRefreshService.refreshAncestors(dto.getIssueId());

        // 发布报表缓存失效事件（工时变更影响 TimeReport/EstimationReport 等统计）
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(issue.getProjectId(), "time_entry_created"));

        // 发布工时通知事件（通知负责人和关注者）
        eventPublisher.publishEvent(new IssueNotificationEvent.TimeLogged(issue, dto.getDuration(), currentUserId));

        // 触发 work_item_added 工作流规则
        eventPublisher.publishEvent(new WorkflowRuleEvent.WorkItemAdded(dto.getIssueId(), issue.getProjectId(), entry.getId()));

        return entry;
    }

    /**
     * 解析工时归属人。
     * 若 forUserId 为空或等于当前用户，归属当前用户（自己记录）。
     * 若 forUserId 指定了其他用户，需要校验：
     *   1. 当前用户拥有 time:log_for_others 权限
     *   2. 目标用户是项目成员
     */
    private Long resolveTargetUser(Long currentUserId, Long forUserId, Long projectId) {
        if (forUserId == null || forUserId.equals(currentUserId)) {
            return currentUserId;
        }
        // 校验当前用户有代录权限
        if (!permissionService.hasPermission(currentUserId, projectId, PERM_TIME_LOG_FOR_OTHERS)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权为他人记录工时");
        }
        // 校验目标用户是项目成员
        projectService.assertProjectMember(forUserId, projectId);
        // 校验目标用户存在
        if (sysUserMapper.selectById(forUserId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标用户不存在");
        }
        return forUserId;
    }

    /**
     * 更新工时记录
     * 权限校验：
     * - 本人工时：需要 time:log 权限
     * - 他人工时：需要 time:edit_all 权限（管理员/技术负责人）
     * 支持孤立工时（工单已删除）的编辑：使用 time_entry.project_id 做权限判断
     */
    @Transactional(rollbackFor = Exception.class)
    public TimeEntry update(Long id, Long userId, UpdateTimeEntryDTO dto) {
        TimeEntry entry = timeEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }

        // 不允许通过标准更新 API 修改正在计时的记录，请使用停止计时器 API
        if (Boolean.TRUE.equals(entry.getOngoing())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "正在计时的记录请使用停止计时器功能");
        }

        // 使用 time_entry 自身的 project_id 做权限判断（解耦 issue 存在性）
        Long entryProjectId = entry.getProjectId();
        boolean isOwnEntry = entry.getUserId().equals(userId);

        if (!isOwnEntry) {
            assertEditAllPermission(userId, entryProjectId);
        } else {
            assertTimeLogPermission(userId, entryProjectId);
        }

        // 如果要转移到另一个工单，校验目标工单权限（目标工单必须活跃）
        if (dto.getIssueId() != null && !dto.getIssueId().equals(entry.getIssueId())) {
            Issue targetIssue = getActiveIssueOrThrow(dto.getIssueId());
            projectService.assertProjectMember(userId, targetIssue.getProjectId());

            // 校验目标项目启用了时间追踪（与 create/startTimer 保持一致）
            if (!projectService.isTimeTrackingEnabled(targetIssue.getProjectId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "目标项目未启用时间追踪功能，无法转移工时记录");
            }

            if (isOwnEntry) {
                assertTimeLogPermission(userId, targetIssue.getProjectId());
            } else {
                assertEditAllPermission(userId, targetIssue.getProjectId());
            }
        }

        // 保存旧值用于活动日志
        Long oldIssueId = entry.getIssueId();
        int oldDuration = entry.getDuration();
        LocalDate oldWorkDate = entry.getWorkDate();
        String oldDescription = entry.getDescription();

        if (dto.getIssueId() != null) {
            entry.setIssueId(dto.getIssueId());
            // 转移工单时同步更新 project_id
            Issue targetIssue = getActiveIssueOrThrow(dto.getIssueId());
            entry.setProjectId(targetIssue.getProjectId());
        }
        if (dto.getWorkDate() != null) entry.setWorkDate(parseAndValidateWorkDate(dto.getWorkDate()));
        if (dto.getDuration() != null) entry.setDuration(dto.getDuration());
        if (dto.getStartTime() != null) entry.setStartTime(dto.getStartTime());
        if (dto.getDescription() != null) entry.setDescription(dto.getDescription());

        // 校验 startTime + duration 组合（使用更新后的值）
        validateStartTimeDurationCombination(entry.getStartTime(), entry.getDuration());

        entry.setUpdatedAt(LocalDateTime.now());

        timeEntryMapper.updateById(entry);

        // 更新工作项属性值（先获取旧值用于活动日志）
        Map<String, Map<String, String>> oldAttrValues = Map.of();
        if (dto.getAttributeValues() != null) {
            oldAttrValues = workItemAttributeService.getTimeEntryAttributeValues(entry.getId());
            Map<Long, Long> attrValueMap = new HashMap<>();
            for (Map.Entry<String, String> av : dto.getAttributeValues().entrySet()) {
                attrValueMap.put(Long.parseLong(av.getKey()), Long.parseLong(av.getValue()));
            }
            workItemAttributeService.saveTimeEntryAttributeValues(entry.getId(), attrValueMap);
        }
        Map<String, Map<String, String>> newAttrValues = dto.getAttributeValues() != null
                ? workItemAttributeService.getTimeEntryAttributeValues(entry.getId())
                : oldAttrValues;

        // 记录活动日志（仅在工单未删除时记录）
        // 获取更新后的工作类型名称（用于 detail JSON）
        String updatedWorkTypeName = resolveWorkTypeNameFromEntry(entry.getId());
        recordTimeUpdateActivity(entry.getId(), userId, oldIssueId, entry.getIssueId(),
                oldDuration, entry.getDuration(),
                oldWorkDate, entry.getWorkDate(),
                oldDescription, entry.getDescription(),
                oldAttrValues, newAttrValues, updatedWorkTypeName);

        // 同步更新 issue.spent_hours（仅在工单未删除时）
        refreshIssueSpentHoursSafe(entry.getIssueId(), userId);
        // 如果工时记录转移到了其他 Issue，旧 Issue 也需要刷新
        if (dto.getIssueId() != null && !oldIssueId.equals(dto.getIssueId())) {
            refreshIssueSpentHoursSafe(oldIssueId, userId);
            // 旧 Issue 的祖先也需要刷新
            ancestorRefreshService.refreshAncestors(oldIssueId);
        }

        // 向上刷新父工单的派生属性
        ancestorRefreshService.refreshAncestors(entry.getIssueId());

        // 发布报表缓存失效事件（工时变更影响 TimeReport/EstimationReport 等统计）
        // 如果跨项目转移，两个项目的缓存都需要失效
        Long currentProjectId = entry.getProjectId();
        if (dto.getIssueId() != null && !oldIssueId.equals(dto.getIssueId())) {
            Issue oldIssue = issueMapper.selectById(oldIssueId);
            if (oldIssue != null && !oldIssue.getProjectId().equals(currentProjectId)) {
                eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(
                        Set.of(currentProjectId, oldIssue.getProjectId()), "time_entry_updated"));
            } else {
                eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(currentProjectId, "time_entry_updated"));
            }
        } else {
            eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(currentProjectId, "time_entry_updated"));
        }

        return entry;
    }

    /**
     * 删除工时记录
     * 权限校验：
     * - 本人工时：需要 time:log 权限
     * - 他人工时：需要 time:delete_all 权限（管理员/技术负责人）
     * 支持孤立工时（工单已删除）的删除：使用 time_entry.project_id 做权限判断
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        TimeEntry entry = timeEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }

        // 使用 time_entry 自身的 project_id 做权限判断（解耦 issue 存在性）
        Long entryProjectId = entry.getProjectId();
        boolean isOwnEntry = entry.getUserId().equals(userId);

        if (!isOwnEntry) {
            assertDeleteAllPermission(userId, entryProjectId);
        } else {
            assertTimeLogPermission(userId, entryProjectId);
        }

        Long issueId = entry.getIssueId();

        // 记录活动：删除了工时（仅在工单仍活跃时记录）
        // ongoing 记录被删除时（丢弃计时器），不记录活动日志（因为没有实际工时被记录）
        if (!Boolean.TRUE.equals(entry.getOngoing())) {
            String durationStr = formatDuration(entry.getDuration());
            // 获取工作类型名称（删除前的最后状态）
            String workTypeName = resolveWorkTypeNameFromEntry(entry.getId());
            String timeEntryDetail = buildTimeEntryDetail(entry.getId(), entry.getWorkDate(),
                    entry.getDuration(), workTypeName, entry.getDescription());
            recordActivitySafeWithDetail(issueId, userId, "time_removed", "spent_time",
                    durationStr, null, timeEntryDetail);
        }

        // 删除属性值关联
        workItemAttributeService.deleteTimeEntryAttributeValues(entry.getId());

        timeEntryMapper.deleteById(id);

        // 同步更新 issue.spent_hours（仅在工单未删除时）
        refreshIssueSpentHoursSafe(issueId, userId);

        // 向上刷新父工单的派生属性
        ancestorRefreshService.refreshAncestors(issueId);

        // 发布报表缓存失效事件（工时变更影响 TimeReport/EstimationReport 等统计）
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(entryProjectId, "time_entry_deleted"));

        // 触发 work_item_deleted 工作流规则
        eventPublisher.publishEvent(new WorkflowRuleEvent.WorkItemDeleted(issueId, entryProjectId, id));
    }

    /**
     * 解析 activityId：向下兼容 workType 名称匹配。
     * 若 activityId 已指定，直接返回；否则尝试从 workType 名称查找对应 ID。
     *
     * @param activityId 工作类型属性值 ID（优先）
     * @param workType   工作类型名称（已废弃，向下兼容）
     * @return 解析后的 activityId，或 null
     */
    public Long resolveActivityId(Long activityId, String workType) {
        if (activityId != null) return activityId;
        if (workType != null && !workType.isBlank()) {
            return workItemAttributeService.findWorkTypeValueIdByName(workType);
        }
        return null;
    }

    /**
     * 查询用户在日期范围内的工时记录（带 issueKey）
     * 支持按项目和工作类型（activityId）筛选
     *
     * @param userId          目标用户 ID（要查看其工时的人）
     * @param viewerUserId    查看者 ID（当前登录用户）
     * @param startDate       开始日期
     * @param endDate         结束日期
     * @param projectId       项目筛选（可选，null 表示不限）
     * @param activityId      工作类型筛选（可选）
     */
    @Transactional(readOnly = true)
    public List<TimeEntryVO> listByUserAndDateRange(Long userId, Long viewerUserId, LocalDate startDate, LocalDate endDate,
                                                     Long projectId, Long activityId) {
        List<Long> allowedProjectIds = null;

        // 查看他人工时时，先校验是否拥有任何项目的 view_others 权限
        if (!userId.equals(viewerUserId)) {
            if (!canViewOthersTime(viewerUserId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
            }
            if (projectId != null) {
                // 指定了项目时，校验查看者对该项目有 time:view_others 权限
                if (!permissionService.hasPermission(viewerUserId, projectId, "time:view_others")) {
                    throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看该项目中他人的工时记录");
                }
                // 权限通过，使用 projectId 做单项目过滤即可
            } else {
                // 未指定项目时，计算查看者有权限的项目范围
                allowedProjectIds = permissionService.getProjectIdsWithPermission(viewerUserId, "time:view_others");
                // null 表示不限（system_admin 或全局作用域角色）
                if (allowedProjectIds != null && allowedProjectIds.isEmpty()) {
                    return List.of(); // 无任何项目的权限，直接返回空
                }
            }
        }

        List<TimeEntryQueryDO> rows = timeEntryMapper.selectEntriesWithIssueKey(
                userId, startDate, endDate, projectId, activityId,
                workItemAttributeService.getWorkTypeAttributeId(), allowedProjectIds);
        return rows.stream().map(timeEntryConverter::queryDOtoVO).toList();
    }

    /**
     * 按工作组汇总工时（工作群组视图）
     * 查询指定用户组内所有成员在日期范围内的工时，按成员分组展示
     *
     * @param groupId       用户组 ID（null 表示查询所有用户组的汇总概览）
     * @param currentUserId 当前登录用户 ID（用于 ongoing 可见性控制和权限检查）
     * @param startDate     开始日期
     * @param endDate       结束日期
     */
    @Transactional(readOnly = true)
    public List<GroupTimeSummaryVO> listByGroup(Long groupId, Long currentUserId, LocalDate startDate, LocalDate endDate) {
        // 获取所有用户组列表（若指定了 groupId，只查该组）
        List<com.trackflow.system.entity.UserGroup> groups;
        if (groupId != null) {
            com.trackflow.system.entity.UserGroup group = groupMapper.selectById(groupId);
            if (group == null) {
                return List.of();
            }
            groups = List.of(group);
        } else {
            groups = groupMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.trackflow.system.entity.UserGroup>()
                            .orderByAsc(com.trackflow.system.entity.UserGroup::getName)
            );
        }

        if (groups.isEmpty()) {
            return List.of();
        }

        Long workTypeAttrId = workItemAttributeService.getWorkTypeAttributeId();

        List<GroupTimeSummaryVO> result = new ArrayList<>();
        for (com.trackflow.system.entity.UserGroup group : groups) {
            // 获取该组的成员 ID 列表
            List<Long> memberUserIds = groupMemberMapper.selectUserIdsByGroupId(group.getId());

            GroupTimeSummaryVO groupVO = new GroupTimeSummaryVO();
            groupVO.setGroupId(String.valueOf(group.getId()));
            groupVO.setGroupName(group.getName());
            groupVO.setGroupDescription(group.getDescription());
            groupVO.setMemberCount(memberUserIds.size());

            if (memberUserIds.isEmpty()) {
                groupVO.setTotalDuration(0);
                groupVO.setMembers(List.of());
                result.add(groupVO);
                continue;
            }

            // 批量查询该组所有成员的工时
            List<TimeEntryQueryDO> rows = timeEntryMapper.selectEntriesByGroupMembers(
                    memberUserIds, startDate, endDate, workTypeAttrId, currentUserId);

            // 按用户分组
            Map<String, List<TimeEntryQueryDO>> byUser = rows.stream()
                    .collect(Collectors.groupingBy(
                            row -> String.valueOf(row.getUserId()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            // 确保每个成员都有对应条目（即使无工时）
            List<GroupTimeSummaryVO.MemberTimeSummaryVO> memberVOs = new ArrayList<>();
            for (Long memberId : memberUserIds) {
                String memberIdStr = String.valueOf(memberId);
                List<TimeEntryQueryDO> memberRows = byUser.getOrDefault(memberIdStr, List.of());

                GroupTimeSummaryVO.MemberTimeSummaryVO memberVO = new GroupTimeSummaryVO.MemberTimeSummaryVO();
                memberVO.setUserId(memberIdStr);

                if (!memberRows.isEmpty()) {
                    TimeEntryQueryDO first = memberRows.get(0);
                    memberVO.setUsername(first.getUserUsername());
                    memberVO.setDisplayName(first.getUserName());
                    memberVO.setAvatarUrl(first.getUserAvatarUrl());
                    List<TimeEntryVO> entryVOs = memberRows.stream().map(row -> {
                        TimeEntryVO vo = timeEntryConverter.queryDOtoVO(row);
                        vo.setUserName(row.getUserName());
                        return vo;
                    }).toList();
                    memberVO.setEntries(entryVOs);
                    memberVO.setTotalDuration(entryVOs.stream().mapToInt(e -> e.getDuration() != null ? e.getDuration() : 0).sum());
                } else {
                    // 无工时的成员：从数据库加载用户信息
                    SysUser user = sysUserMapper.selectById(memberId);
                    if (user != null) {
                        memberVO.setUsername(user.getUsername());
                        memberVO.setDisplayName(user.getDisplayName());
                        memberVO.setAvatarUrl(user.getAvatarUrl());
                    }
                    memberVO.setEntries(List.of());
                    memberVO.setTotalDuration(0);
                }
                memberVOs.add(memberVO);
            }

            // 按工时倒序排列（工时多的成员排前面）
            memberVOs.sort((a, b) -> Integer.compare(b.getTotalDuration(), a.getTotalDuration()));

            groupVO.setMembers(memberVOs);
            groupVO.setTotalDuration(memberVOs.stream().mapToInt(GroupTimeSummaryVO.MemberTimeSummaryVO::getTotalDuration).sum());
            result.add(groupVO);
        }

        return result;
    }

    /**
     * 查询某 Issue 的工时记录列表（按工作日期降序）
     * ongoing 记录仅对其所有者（currentUserId）可见
     *
     * @param issueId       工单 ID
     * @param currentUserId 当前登录用户 ID（用于 ongoing 可见性控制）
     */
    @Transactional(readOnly = true)
    public List<TimeEntryVO> listByIssue(Long issueId, Long currentUserId) {
        QueryWrapper<TimeEntry> wrapper = new QueryWrapper<TimeEntry>()
                .eq("issue_id", issueId)
                .and(w -> w.eq("ongoing", false).or().eq("user_id", currentUserId))
                .orderByDesc("work_date", "created_at");
        List<TimeEntry> entries = timeEntryMapper.selectList(wrapper);
        if (entries.isEmpty()) return List.of();

        // 批量加载用户名（含 loggedBy 用户）
        Set<Long> userIds = new HashSet<>();
        entries.forEach(e -> {
            userIds.add(e.getUserId());
            if (e.getLoggedBy() != null) userIds.add(e.getLoggedBy());
        });
        Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, user ->
                        user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()));

        // 批量加载属性值（含 workType）
        String ids = entries.stream().map(e -> String.valueOf(e.getId())).collect(Collectors.joining(","));
        Map<Long, List<Map<String, Object>>> attrByEntry = workItemAttributeService.getAttributeValuesForEntries(ids);
        Long workTypeAttrId = workItemAttributeService.getWorkTypeAttributeId();

        return entries.stream().map(entry -> {
            TimeEntryVO vo = timeEntryConverter.toVO(entry);

            // 填充 userName
            vo.setUserName(userNameMap.getOrDefault(entry.getUserId(), "未知用户"));

            // loggedBy 名称（仅代录场景）
            if (entry.getLoggedBy() != null && !entry.getLoggedBy().equals(entry.getUserId())) {
                vo.setLoggedByName(userNameMap.getOrDefault(entry.getLoggedBy(), "未知用户"));
            }

            // 填充属性值列表 + 工作类型
            List<Map<String, Object>> entryAttrs = attrByEntry.get(entry.getId());
            if (entryAttrs != null && !entryAttrs.isEmpty()) {
                vo.setAttributeValues(buildAttributeValueVOs(entryAttrs));
                // 提取 Work type
                entryAttrs.stream()
                        .filter(row -> workTypeAttrId.equals(((Number) row.get("attribute_id")).longValue()))
                        .findFirst()
                        .ifPresent(row -> {
                            vo.setWorkType((String) row.get("value_name"));
                            vo.setWorkTypeId(String.valueOf(row.get("value_id")));
                            vo.setWorkTypeColor((String) row.get("value_color"));
                        });
            }

            return vo;
        }).toList();
    }

    /**
     * 汇总用户在日期范围内的总工时（分钟）
     * 排除 ongoing=true 的记录（仍在计时中，duration 为 NULL）
     * 带项目范围限制——查看他人汇总时只计入有权限项目的工时
     *
     * @param targetUserId 目标用户 ID
     * @param viewerUserId 查看者 ID（当前登录用户）
     * @param startDate    开始日期
     * @param endDate      结束日期
     */
    @Transactional(readOnly = true)
    public int sumByUserAndDateRange(Long targetUserId, Long viewerUserId, LocalDate startDate, LocalDate endDate) {
        List<Long> allowedProjectIds = null;

        if (!targetUserId.equals(viewerUserId)) {
            if (!canViewOthersTime(viewerUserId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
            }
            allowedProjectIds = permissionService.getProjectIdsWithPermission(viewerUserId, "time:view_others");
            if (allowedProjectIds != null && allowedProjectIds.isEmpty()) {
                return 0;
            }
        }

        QueryWrapper<TimeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", targetUserId)
                .eq("ongoing", false)
                .ge("work_date", startDate)
                .le("work_date", endDate);

        if (allowedProjectIds != null) {
            wrapper.in("project_id", allowedProjectIds);
        }

        List<TimeEntry> entries = timeEntryMapper.selectList(wrapper.select("duration"));
        return entries.stream().mapToInt(e -> e.getDuration() != null ? e.getDuration() : 0).sum();
    }

    /**
     * 按项目汇总工时（项目视图概览）：返回用户可见项目的工时聚合
     */
    @Transactional(readOnly = true)
    public List<ProjectTimeSummaryVO> listByProjectForUser(Long userId, LocalDate startDate, LocalDate endDate) {
        // 获取用户可访问的项目列表（包含直接成员 + 用户组成员 + 可见项目）
        // null 表示无限制（system_admin）
        List<Long> allowedProjectIds = projectService.getAccessibleProjectIds(userId);
        if (allowedProjectIds != null && allowedProjectIds.isEmpty()) {
            return List.of();
        }

        List<TimeEntryQueryDO> rows = timeEntryMapper.selectEntriesByProjectForUser(
                userId, startDate, endDate, workItemAttributeService.getWorkTypeAttributeId(), allowedProjectIds);

        // 按 project_id 分组
        Map<String, List<TimeEntryQueryDO>> grouped = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.getProjectId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ProjectTimeSummaryVO> result = new ArrayList<>();
        for (Map.Entry<String, List<TimeEntryQueryDO>> entry : grouped.entrySet()) {
            List<TimeEntryQueryDO> projectRows = entry.getValue();
            TimeEntryQueryDO first = projectRows.get(0);

            ProjectTimeSummaryVO vo = new ProjectTimeSummaryVO();
            vo.setProjectId(String.valueOf(first.getProjectId()));
            vo.setProjectName(first.getProjectName());
            vo.setProjectKey(first.getProjectKey());

            List<TimeEntryVO> entries = projectRows.stream().map(timeEntryConverter::queryDOtoVO).toList();
            vo.setEntries(entries);
            vo.setTotalDuration(entries.stream().mapToInt(e -> e.getDuration() != null ? e.getDuration() : 0).sum());

            result.add(vo);
        }
        return result;
    }

    /**
     * 查询指定项目在日期范围内的工时明细（项目视图详情）
     * ongoing 记录仅对其所有者可见，他人的 ongoing 记录不返回
     */
    @Transactional(readOnly = true)
    public List<TimeEntryVO> listByProject(Long projectId, LocalDate startDate, LocalDate endDate, Long currentUserId) {
        List<TimeEntryQueryDO> rows = timeEntryMapper.selectEntriesByProject(
                projectId, startDate, endDate, workItemAttributeService.getWorkTypeAttributeId(), currentUserId);
        return rows.stream().map(row -> {
            TimeEntryVO vo = timeEntryConverter.queryDOtoVO(row);
            vo.setUserName(row.getUserName());
            return vo;
        }).toList();
    }

    /**
     * 获取可选择的用户列表
     * - canViewOthers=true: 返回所有活跃用户（支持关键字搜索）
     * - canViewOthers=false: 仅返回当前用户自身
     */
    @Transactional(readOnly = true)
    public List<TimeEntryUserVO> listSelectableUsers(Long currentUserId, String keyword) {
        boolean canViewOthers = canViewOthersTime(currentUserId);
        if (!canViewOthers) {
            // 普通用户仅返回自身
            SysUser self = sysUserMapper.selectById(currentUserId);
            if (self == null) return List.of();
            return List.of(toUserVO(self));
        }

        // 管理员查看所有活跃用户
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active");
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like("display_name", kw).or().like("username", kw));
        }
        wrapper.orderByAsc("display_name");
        wrapper.last("LIMIT 50");

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        return users.stream().map(this::toUserVO).toList();
    }

    private TimeEntryUserVO toUserVO(SysUser user) {
        TimeEntryUserVO vo = new TimeEntryUserVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
    }

    // ========== 权限查询 ==========

    /**
     * 检查用户是否有权查看他人工时（在任意项目中拥有 time:view_others 权限）
     */
    public boolean canViewOthersTime(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, "time:view_others");
    }

    /**
     * 检查用户是否有权编辑/删除他人工时（在任意项目中拥有 time:edit_all 权限）
     */
    public boolean canEditOthersTime(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, PERM_TIME_EDIT_ALL);
    }

    /**
     * 检查用户是否有权为他人记录工时（在任意项目中拥有 time:log_for_others 权限）
     */
    public boolean canLogForOthers(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, PERM_TIME_LOG_FOR_OTHERS);
    }

    // ========== 内部方法 ==========

    /**
     * 解析并校验工作日期：
     * - 格式必须为 yyyy-MM-dd（DTO 层 @Pattern 已兜底，此处做二次防御）
     * - 不允许未来日期
     * - 不允许超过 MAX_PAST_DAYS 天前的日期
     */
    private LocalDate parseAndValidateWorkDate(String workDateStr) {
        LocalDate workDate;
        try {
            workDate = LocalDate.parse(workDateStr);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "日期格式无效，请使用 yyyy-MM-dd 格式");
        }

        LocalDate today = LocalDate.now();
        if (workDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不允许记录未来日期的工时");
        }
        if (workDate.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不允许记录超过" + MAX_PAST_DAYS + "天前的工时");
        }
        return workDate;
    }

    /**
     * 校验 startTime + duration 组合不超出一天范围。
     * 仅在 startTime 非 null 时校验。
     */
    private void validateStartTimeDurationCombination(Integer startTime, Integer duration) {
        if (startTime != null && duration != null) {
            if (startTime + duration > MAX_DURATION_MINUTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "开始时间 + 时长不能超过1440分钟（一天），当前：" + startTime + " + " + duration + " = " + (startTime + duration));
            }
        }
    }

    /**
     * 从 attributeValues 中解析 Work type 名称
     */
    private String resolveWorkTypeName(Map<String, String> attributeValues) {
        if (attributeValues == null || attributeValues.isEmpty()) return null;
        String workTypeAttrId = String.valueOf(workItemAttributeService.getWorkTypeAttributeId());
        String valueIdStr = attributeValues.get(workTypeAttrId);
        if (valueIdStr == null) return null;
        try {
            return workItemAttributeService.getAttributeValueName(Long.parseLong(valueIdStr));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 批量加载工时记录的 Work type 属性信息
     * 返回 Map<timeEntryId, Map<"id"/"name"/"color", value>>
     */
    private Map<Long, Map<String, String>> loadWorkTypeForEntries(String timeEntryIds) {
        Map<Long, Map<String, String>> result = new HashMap<>();
        try {
            var rows = workItemAttributeService.getWorkTypeForEntries(timeEntryIds);
            for (Map<String, Object> row : rows) {
                Long entryId = ((Number) row.get("time_entry_id")).longValue();
                Map<String, String> info = new HashMap<>();
                info.put("id", String.valueOf(row.get("value_id")));
                info.put("name", (String) row.get("value_name"));
                info.put("color", (String) row.get("value_color"));
                result.put(entryId, info);
            }
        } catch (Exception ignored) {
            // Graceful degradation: if attribute lookup fails, entries just won't have work type info
        }
        return result;
    }

    /**
     * 原子更新 Issue 的 spent_hours 字段。
     * 使用子查询方式直接在数据库层聚合，防止并发 lost update。
     * 同时更新 updated_at/updated_by，确保工时变更反映为工单修改。
     */
    private void refreshIssueSpentHours(Long issueId, Long updatedBy) {
        timeEntryMapper.atomicRefreshSpentHours(issueId, updatedBy);
    }

    /**
     * 安全版本：仅在 issue 未被软删除时刷新 spent_hours。
     * 用于工时编辑/删除场景——工单已删除时无需更新其 spent_hours。
     */
    private void refreshIssueSpentHoursSafe(Long issueId, Long updatedBy) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue != null) {
            // selectById 受逻辑删除过滤，能查到说明 issue 未删除
            timeEntryMapper.atomicRefreshSpentHours(issueId, updatedBy);
        }
    }

    /**
     * 启动计时器。
     * 创建一条 ongoing=true 的 time_entry 记录，duration 为 NULL。
     * 系统根据 created_at 实时计算已用时间。
     *
     * 约束：每个用户同时只能有一个活跃计时器（数据库 UNIQUE 部分索引保证）。
     *
     * @param currentUserId 当前用户 ID
     * @param dto 启动参数（issueId 必填）
     * @return 创建的计时器 time_entry 记录
     */
    @Transactional(rollbackFor = Exception.class)
    public TimeEntry startTimer(Long currentUserId, StartTimerDTO dto) {
        // 1. 校验工单存在且未软删除
        Issue issue = getActiveIssueOrThrow(dto.getIssueId());

        // 2. 校验当前用户为项目成员且有 time:log 权限
        projectService.assertProjectMember(currentUserId, issue.getProjectId());
        assertTimeLogPermission(currentUserId, issue.getProjectId());

        // 3. 校验项目是否启用了时间追踪
        if (!projectService.isTimeTrackingEnabled(issue.getProjectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该项目未启用时间追踪功能");
        }

        // 4. 检查是否已有活跃计时器
        TimeEntry activeTimer = getActiveTimerForUser(currentUserId);
        if (activeTimer != null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "已有一个正在运行的计时器，请先停止后再启动新的");
        }

        // 5. 创建 ongoing time_entry
        TimeEntry entry = new TimeEntry();
        entry.setIssueId(dto.getIssueId());
        entry.setProjectId(issue.getProjectId());
        entry.setUserId(currentUserId);
        entry.setLoggedBy(currentUserId);
        entry.setWorkDate(LocalDate.now());
        entry.setDuration(null);   // ongoing 时无 duration
        entry.setStartTime(minutesFromMidnight()); // 记录启动时刻
        entry.setDescription(dto.getDescription());
        entry.setOngoing(true);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        try {
            timeEntryMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            // 并发竞态：UNIQUE 部分索引 idx_time_entry_user_ongoing 拦截了重复的 ongoing 记录
            throw new BusinessException(ErrorCode.CONFLICT,
                    "已有一个正在运行的计时器，请先停止后再启动新的");
        }

        // 保存工作项属性值
        if (dto.getAttributeValues() != null && !dto.getAttributeValues().isEmpty()) {
            Map<Long, Long> attrValueMap = new HashMap<>();
            for (Map.Entry<String, String> av : dto.getAttributeValues().entrySet()) {
                attrValueMap.put(Long.parseLong(av.getKey()), Long.parseLong(av.getValue()));
            }
            workItemAttributeService.saveTimeEntryAttributeValues(entry.getId(), attrValueMap);
        }

        return entry;
    }

    /**
     * 停止计时器。
     * 计算 duration = now - createdAt（分钟），设置 ongoing=false，保存。
     *
     * @param currentUserId 当前用户 ID
     * @param timeEntryId 要停止的计时器 ID
     * @param dto 停止参数（可选的 duration 覆盖、description、attributeValues）
     * @return 更新后的 time_entry 记录
     */
    @Transactional(rollbackFor = Exception.class)
    public TimeEntry stopTimer(Long currentUserId, Long timeEntryId, StopTimerDTO dto) {
        TimeEntry entry = timeEntryMapper.selectById(timeEntryId);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }

        // 校验是自己的计时器
        if (!entry.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只能停止自己的计时器");
        }

        // 校验确实是 ongoing 状态
        if (!Boolean.TRUE.equals(entry.getOngoing())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该工时记录不是正在计时的状态");
        }

        // 计算 duration
        int calculatedDuration = calculateElapsedMinutes(entry.getCreatedAt());
        int finalDuration;
        if (dto != null && dto.getDuration() != null) {
            // 用户手动覆盖
            finalDuration = dto.getDuration();
        } else {
            finalDuration = Math.max(1, calculatedDuration); // 至少 1 分钟
        }

        // 跨天处理（对齐 OpenProject：spent_on 保持启动日不修改）
        // 如果计时器跨天（启动日 != 当前日期），清除 startTime（无法用单一 startTime 表达跨天场景）
        // workDate 保持为计时器启动时的日期（"工作发生在那天"语义）
        boolean crossDay = !entry.getWorkDate().equals(LocalDate.now());
        if (crossDay) {
            // 跨天：保留原始 workDate，清除 startTime
            entry.setStartTime(null);
        }
        // 同天：workDate 和 startTime 均保持不变

        entry.setDuration(finalDuration);
        entry.setOngoing(false);
        entry.setUpdatedAt(LocalDateTime.now());

        // 可选更新描述和属性
        if (dto != null) {
            if (dto.getDescription() != null) {
                entry.setDescription(dto.getDescription());
            }
            if (dto.getAttributeValues() != null && !dto.getAttributeValues().isEmpty()) {
                Map<Long, Long> attrValueMap = new HashMap<>();
                for (Map.Entry<String, String> av : dto.getAttributeValues().entrySet()) {
                    attrValueMap.put(Long.parseLong(av.getKey()), Long.parseLong(av.getValue()));
                }
                workItemAttributeService.saveTimeEntryAttributeValues(entry.getId(), attrValueMap);
            }
        }

        timeEntryMapper.updateById(entry);

        // 记录活动日志
        String durationStr = formatDuration(finalDuration);
        String workTypeName = null;
        if (dto != null && dto.getAttributeValues() != null) {
            workTypeName = resolveWorkTypeName(dto.getAttributeValues());
        }
        String newValueStr = workTypeName != null ? durationStr + " | " + workTypeName : durationStr;
        if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
            newValueStr += " | " + entry.getDescription();
        }
        newValueStr += " (计时器)";
        // 构建结构化 detail JSON
        String timeEntryDetail = buildTimeEntryDetail(entry.getId(), entry.getWorkDate(), finalDuration,
                workTypeName, entry.getDescription());
        recordActivityWithDetail(entry.getIssueId(), currentUserId, "time_logged", "spent_time",
                null, newValueStr, timeEntryDetail);

        // 同步更新 issue.spent_hours + updated_at
        refreshIssueSpentHours(entry.getIssueId(), currentUserId);

        // 向上刷新父工单的派生属性
        ancestorRefreshService.refreshAncestors(entry.getIssueId());

        // 发布报表缓存失效事件（计时器停止 = 新增工时记录）
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(entry.getProjectId(), "time_entry_created"));

        return entry;
    }

    /**
     * 获取当前用户的活跃计时器。
     * 每个用户最多一个（数据库 UNIQUE 索引保证）。
     *
     * @param userId 用户 ID
     * @return 活跃的 time_entry，或 null 如果没有
     */
    public TimeEntry getActiveTimerForUser(Long userId) {
        QueryWrapper<TimeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("ongoing", true);
        return timeEntryMapper.selectOne(wrapper);
    }

    /**
     * 获取活跃计时器的 VO（含 issue 信息），供前端展示。
     */
    public TimeEntryVO getActiveTimerVO(Long userId) {
        TimeEntry entry = getActiveTimerForUser(userId);
        if (entry == null) return null;
        return buildEntryVO(entry);
    }

    /**
     * 构建 TimeEntryVO（含关联信息：issueKey/issueTitle/属性值/loggedByName）。
     * 此方法封装了所有 Entity→VO 的转换与关联查询逻辑，供 Controller 和内部列表方法统一调用。
     *
     * @param entry 工时记录实体
     * @return 完整的 TimeEntryVO
     */
    @Transactional(readOnly = true)
    public TimeEntryVO buildEntryVO(TimeEntry entry) {
        TimeEntryVO vo = timeEntryConverter.toVO(entry);

        // 工单信息（issueKey + issueTitle），计时器 badge 全局显示需要
        enrichWithIssueInfo(vo, entry.getIssueId());

        // 用户名
        vo.setUserName(getUserDisplayName(entry.getUserId()));

        // loggedBy 名称（仅代录场景）
        if (entry.getLoggedBy() != null && !entry.getLoggedBy().equals(entry.getUserId())) {
            vo.setLoggedByName(getUserDisplayName(entry.getLoggedBy()));
        }

        // 加载属性值
        enrichWithAttributeValues(vo, entry.getId());

        return vo;
    }

    /**
     * 填充工单关联信息（issueKey/issueTitle/issueDeleted）
     */
    private void enrichWithIssueInfo(TimeEntryVO vo, Long issueId) {
        try {
            Issue issue = issueMapper.selectById(issueId);
            if (issue != null) {
                vo.setIssueKey(issue.getIssueKey());
                vo.setIssueTitle(issue.getTitle());
                vo.setIssueDeleted(false);
            } else {
                vo.setIssueDeleted(true);
            }
        } catch (Exception e) {
            vo.setIssueDeleted(true);
        }
    }

    /**
     * 填充工作项属性值（workType/workTypeColor/attributeValues）
     */
    private void enrichWithAttributeValues(TimeEntryVO vo, Long timeEntryId) {
        Map<String, Map<String, String>> attrValues = workItemAttributeService.getTimeEntryAttributeValues(timeEntryId);
        if (attrValues.isEmpty()) return;

        vo.setAttributeValues(attrValues.entrySet().stream().map(e -> {
            TimeEntryAttributeValueVO item = new TimeEntryAttributeValueVO();
            item.setAttributeId(e.getKey());
            item.setAttributeName(e.getValue().get("attributeName"));
            item.setValueId(e.getValue().get("valueId"));
            item.setValueName(e.getValue().get("valueName"));
            item.setValueColor(e.getValue().get("valueColor"));
            return item;
        }).toList());

        // Extract Work type specifically using dynamic attribute ID
        String workTypeAttrId = String.valueOf(workItemAttributeService.getWorkTypeAttributeId());
        Map<String, String> workTypeInfo = attrValues.get(workTypeAttrId);
        if (workTypeInfo != null) {
            vo.setWorkType(workTypeInfo.get("valueName"));
            vo.setWorkTypeId(workTypeInfo.get("valueId"));
            vo.setWorkTypeColor(workTypeInfo.get("valueColor"));
        }
    }

    /**
     * 从批量查询的属性值行数据构建 TimeEntryAttributeValueVO 列表。
     * 每行包含: time_entry_id, attribute_id, attribute_name, value_id, value_name, value_color
     */
    private List<TimeEntryAttributeValueVO> buildAttributeValueVOs(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            TimeEntryAttributeValueVO item = new TimeEntryAttributeValueVO();
            item.setAttributeId(String.valueOf(row.get("attribute_id")));
            item.setAttributeName((String) row.get("attribute_name"));
            item.setValueId(String.valueOf(row.get("value_id")));
            item.setValueName((String) row.get("value_name"));
            item.setValueColor((String) row.get("value_color"));
            return item;
        }).toList();
    }

    /**
     * 计算从 createdAt 到现在经过的分钟数（向上取整）。
     * 参考 YouTrack：不足 1 分钟向上取整为 1m，61 秒记为 2m。
     */
    private int calculateElapsedMinutes(LocalDateTime createdAt) {
        if (createdAt == null) return 0;
        long seconds = java.time.Duration.between(createdAt, LocalDateTime.now()).getSeconds();
        if (seconds <= 0) return 0;
        return (int) Math.ceil(seconds / 60.0);
    }

    /**
     * 获取当前时刻距离午夜的分钟数，用作 startTime。
     */
    private int minutesFromMidnight() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.getHour() * 60 + now.getMinute();
    }

    /**
     * 全量校准所有 issue 的 spent_hours（管理员自愈操作）。
     * 一次性将所有 issue.spent_hours 与 time_entry 实际数据对齐。
     *
     * @return 受影响的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalculateAllSpentHours() {
        return timeEntryMapper.recalculateAllSpentHours();
    }

    /**
     * 记录工时更新的活动日志。
     * - 工时跨工单转移：旧工单记录 time_removed，新工单记录 time_logged
     * - 普通字段修改（时长、日期、描述）：记录 time_updated
     */
    private void recordTimeUpdateActivity(Long entryId, Long userId, Long oldIssueId, Long newIssueId,
                                          int oldDuration, int newDuration,
                                          LocalDate oldWorkDate, LocalDate newWorkDate,
                                          String oldDescription, String newDescription,
                                          Map<String, Map<String, String>> oldAttrValues,
                                          Map<String, Map<String, String>> newAttrValues,
                                          String newWorkTypeName) {
        boolean issueChanged = !oldIssueId.equals(newIssueId);

        if (issueChanged) {
            // 工时转移：旧工单记录"工时被移走"，新工单记录"工时被移入"
            String durationStr = formatDuration(newDuration);
            String detail = buildTimeEntryDetail(entryId, newWorkDate, newDuration, newWorkTypeName, newDescription);
            recordActivitySafeWithDetail(oldIssueId, userId, "time_removed", "spent_time", durationStr, null, detail);
            recordActivitySafeWithDetail(newIssueId, userId, "time_logged", "spent_time", null, durationStr, detail);
            return;
        }

        // 普通字段修改：记录 time_updated，包含变更明细
        List<String> changes = new ArrayList<>();
        if (oldDuration != newDuration) {
            changes.add("时长: " + formatDuration(oldDuration) + " → " + formatDuration(newDuration));
        }
        if (!Objects.equals(oldWorkDate, newWorkDate)) {
            changes.add("日期: " + oldWorkDate + " → " + newWorkDate);
        }
        if (!Objects.equals(oldDescription, newDescription)) {
            String from = oldDescription != null && !oldDescription.isBlank() ? oldDescription : "无";
            String to = newDescription != null && !newDescription.isBlank() ? newDescription : "无";
            changes.add("描述: " + from + " → " + to);
        }

        // 比较属性值变化（如 Work Type）
        changes.addAll(buildAttributeChangeDescriptions(oldAttrValues, newAttrValues));

        if (!changes.isEmpty()) {
            String oldValueStr = formatDuration(oldDuration);
            String newValueStr = String.join("; ", changes);
            String detail = buildTimeEntryDetail(entryId, newWorkDate, newDuration, newWorkTypeName, newDescription);
            recordActivitySafeWithDetail(oldIssueId, userId, "time_updated", "spent_time",
                    oldValueStr, newValueStr, detail);
        }
    }

    /**
     * 比较工时属性值变化，生成人类可读的变更描述列表。
     * 支持属性新增、修改和移除三种情况。
     */
    private List<String> buildAttributeChangeDescriptions(Map<String, Map<String, String>> oldAttrValues,
                                                          Map<String, Map<String, String>> newAttrValues) {
        List<String> changes = new ArrayList<>();

        // 收集所有涉及的属性 ID
        Set<String> allAttrIds = new HashSet<>();
        allAttrIds.addAll(oldAttrValues.keySet());
        allAttrIds.addAll(newAttrValues.keySet());

        for (String attrId : allAttrIds) {
            Map<String, String> oldVal = oldAttrValues.get(attrId);
            Map<String, String> newVal = newAttrValues.get(attrId);

            String oldValueName = oldVal != null ? oldVal.get("valueName") : null;
            String newValueName = newVal != null ? newVal.get("valueName") : null;

            if (!Objects.equals(oldValueName, newValueName)) {
                // 获取属性名称用于日志展示
                String attrName = resolveAttributeName(attrId);
                String from = oldValueName != null ? oldValueName : "无";
                String to = newValueName != null ? newValueName : "无";
                changes.add(attrName + ": " + from + " → " + to);
            }
        }

        return changes;
    }

    /**
     * 根据属性 ID 获取属性名称（用于活动日志展示）
     */
    private String resolveAttributeName(String attrId) {
        try {
            return workItemAttributeService.getAttributeNameById(Long.parseLong(attrId));
        } catch (Exception e) {
            return "属性#" + attrId;
        }
    }

    private void recordActivity(Long issueId, Long userId, String action, String fieldName, String oldValue, String newValue) {
        recordActivityWithDetail(issueId, userId, action, fieldName, oldValue, newValue, null);
    }

    private void recordActivityWithDetail(Long issueId, Long userId, String action, String fieldName,
                                          String oldValue, String newValue, String detail) {
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setDetail(detail);
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    /**
     * 安全版本：仅在 issue 未被软删除时记录活动日志（无 detail）。
     */
    private void recordActivitySafe(Long issueId, Long userId, String action, String fieldName, String oldValue, String newValue) {
        recordActivitySafeWithDetail(issueId, userId, action, fieldName, oldValue, newValue, null);
    }

    /**
     * 安全版本：仅在 issue 未被软删除时记录活动日志（含 detail）。
     * 工单已删除时无法写入活动记录（外键约束或逻辑无意义）。
     */
    private void recordActivitySafeWithDetail(Long issueId, Long userId, String action, String fieldName,
                                              String oldValue, String newValue, String detail) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue != null) {
            recordActivityWithDetail(issueId, userId, action, fieldName, oldValue, newValue, detail);
        }
    }

    /**
     * 构建工时条目的结构化 detail JSON 字符串，供前端多列渲染使用。
     * 格式：{"type":"time_entry","entryId":123,"workDate":"2026-07-20","duration":150,"workType":"Development","description":"..."}
     */
    private String buildTimeEntryDetail(Long entryId, LocalDate workDate, int duration,
                                        String workType, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"time_entry\"");
        if (entryId != null) {
            sb.append(",\"entryId\":").append(entryId);
        }
        if (workDate != null) {
            sb.append(",\"workDate\":\"").append(workDate).append("\"");
        }
        sb.append(",\"duration\":").append(duration);
        if (workType != null && !workType.isBlank()) {
            sb.append(",\"workType\":\"").append(escapeJson(workType)).append("\"");
        }
        if (description != null && !description.isBlank()) {
            sb.append(",\"description\":\"").append(escapeJson(description)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 从已保存的工时记录的属性值中解析工作类型名称。
     * 用于删除、更新场景——此时 DTO 的 attributeValues 已不可用。
     */
    private String resolveWorkTypeNameFromEntry(Long entryId) {
        try {
            Map<String, Map<String, String>> attrValues = workItemAttributeService.getTimeEntryAttributeValues(entryId);
            if (attrValues.isEmpty()) return null;
            // 取第一个属性的 valueName 作为工作类型
            return attrValues.values().stream()
                    .map(v -> v.get("valueName"))
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 字符串转义（仅处理双引号、反斜杠和换行符）。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String formatDuration(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h" + m + "m";
    }

    /**
     * 获取活跃的 Issue（未软删除），不存在或已删除则抛异常
     */
    private Issue getActiveIssueOrThrow(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            // selectById 受逻辑删除过滤，再查一次确认是不存在还是已软删除
            com.trackflow.issue.mapper.result.DeletedIssueRow raw = issueMapper.selectByIdIgnoreDeleted(issueId);
            if (raw != null && raw.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "工单已删除，无法操作工时");
            }
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在");
        }
        return issue;
    }

    /**
     * 校验用户是否拥有 time:log 权限（项目级）
     */
    private void assertTimeLogPermission(Long userId, Long projectId) {
        if (!permissionService.hasPermission(userId, projectId, PERM_TIME_LOG)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权记录工时");
        }
    }

    /**
     * 校验用户是否拥有 time:edit_all 权限（项目级）
     * 用于管理员编辑他人工时记录
     */
    private void assertEditAllPermission(Long userId, Long projectId) {
        if (!permissionService.hasPermission(userId, projectId, PERM_TIME_EDIT_ALL)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权修改他人工时记录");
        }
    }

    /**
     * 校验用户是否拥有 time:delete_all 权限（项目级）
     * 用于管理员删除他人工时记录
     */
    private void assertDeleteAllPermission(Long userId, Long projectId) {
        if (!permissionService.hasPermission(userId, projectId, PERM_TIME_DELETE_ALL)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权删除他人工时记录");
        }
    }

    /**
     * 获取用户显示名称，用于日志记录和 VO 构建
     */
    private String getUserDisplayName(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return "未知用户";
        return user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
    }
}

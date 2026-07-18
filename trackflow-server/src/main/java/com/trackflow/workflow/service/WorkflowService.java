package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.converter.RoleConverter;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.vo.RoleVO;
import com.trackflow.workflow.WorkflowScope;
import com.trackflow.workflow.converter.WorkflowConverter;
import com.trackflow.workflow.dto.UpdateWorkflowDTO;
import com.trackflow.workflow.dto.WorkflowActivityQuery;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.entity.WorkflowVersion;
import com.trackflow.workflow.mapper.WorkflowActivityMapper;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import com.trackflow.workflow.mapper.WorkflowVersionMapper;
import com.trackflow.workflow.vo.WorkflowMatrixVO;
import com.trackflow.workflow.vo.WorkflowTransitionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowTransitionMapper transitionMapper;
    private final WorkflowActivityMapper activityMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowConverter workflowConverter;
    private final IssueStatusMapper statusMapper;
    private final IssueMapper issueMapper;
    private final ProjectMemberMapper memberMapper;
    private final SysRoleMapper roleMapper;
    private final RoleConverter roleConverter;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    /**
     * 不再使用 ownership 限制。
     * 
     * 设计原则（对标 YouTrack）：
     * - 拥有 issue:change_status 权限的用户可以变更项目内任意工单的状态
     * - 可用转换路径已通过 workflow_transition 表按角色充分约束
     * - 不需要额外的"是否是 assignee"校验
     * - observer 角色本身在 workflow_transition 中无规则，因此天然无法转换
     */

    /**
     * project_admin 的角色 ID（来自 V2__seed_roles.sql 种子数据，ID 固定为 2）。
     * 系统管理员无项目角色时，使用此角色的工作流规则。
     */
    private static final Long PROJECT_ADMIN_ROLE_ID = 2L;

    /**
     * 获取当前用户对指定 Issue 可以转换到的目标状态列表。
     * 
     * 权限模型（对标 YouTrack）：
     * - system_admin：全局权限，使用 project_admin 的工作流规则
     * - 项目成员：根据其角色在 workflow_transition 表中定义的规则确定可用转换
     * - 非项目成员：无可用转换
     * 
     * author/assignee 维度（对标 OpenProject Workflow.from_status）：
     * - 始终包含基础规则（author=false AND assignee=false）
     * - 如果用户是工单创建者，额外包含 author=true 的规则
     * - 如果用户是工单负责人，额外包含 assignee=true 的规则
     */
    public List<IssueStatus> getAvailableTransitions(Issue issue, Long userId) {
        boolean isAuthor = userId.equals(issue.getCreatedBy());
        boolean isAssignee = userId.equals(issue.getAssigneeId());

        // 系统管理员直接跳过所有权检查（由全局权限保障）
        if (permissionService.isSystemAdmin(userId)) {
            // 系统管理员使用 project_admin 的工作流规则
            // 系统管理员同时视为 author + assignee（获取最大权限集）
            return getTransitionsForRoles(issue, List.of(PROJECT_ADMIN_ROLE_ID), true, true);
        }

        // 获取用户在项目中的角色
        List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(userId, issue.getProjectId());
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return getTransitionsForRoles(issue, roleIds, isAuthor, isAssignee);
    }

    /**
     * 检查用户是否为工单的"所有者"（assignee 或 reporter 或创建者）。
     *
     * @deprecated 此方法的逻辑已被 author/assignee 查询维度替代。
     *             工作流引擎现在通过 workflow_transition 表的 author/assignee 字段
     *             直接在转换规则查询中处理创建者/负责人的额外权限。
     *             计划在下个版本移除。
     */
    @Deprecated(since = "2026-07-17", forRemoval = true)
    public boolean isIssueOwner(Issue issue, Long userId) {
        return Objects.equals(issue.getAssigneeId(), userId)
                || Objects.equals(issue.getReporterId(), userId)
                || Objects.equals(issue.getCreatedBy(), userId);
    }

    /**
     * 根据角色 ID 列表查询可用的状态转换。
     * <p>
     * 使用 4 级优先级覆盖语义（与 ActionResolver 对齐）：
     * <ol>
     *   <li>project_id = X AND issue_type = 精确类型</li>
     *   <li>project_id = X AND issue_type = '*'</li>
     *   <li>project_id IS NULL AND issue_type = 精确类型</li>
     *   <li>project_id IS NULL AND issue_type = '*'</li>
     * </ol>
     * 返回最高优先级非空层级的结果（项目级规则存在时不合并全局规则）。
     */
    private List<IssueStatus> getTransitionsForRoles(Issue issue, List<Long> roleIds,
                                                     boolean isAuthor, boolean isAssignee) {
        List<Long> allowedStatusIds = resolveAllowedStatusIds(
                issue.getProjectId(), issue.getIssueType(), roleIds, issue.getStatusId(),
                isAuthor, isAssignee);

        if (allowedStatusIds.isEmpty()) {
            return List.of();
        }

        return statusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().in(IssueStatus::getId, allowedStatusIds)
                        .orderByAsc(IssueStatus::getSortOrder)
        );
    }

    /**
     * 4 级优先级解析允许的目标状态 ID（优化版：单次 DB 查询）。
     * <p>
     * 将所有 4 级规则通过单次 SQL 查询获取，附带 priority_level 标记，
     * 然后在 Java 层筛选出最高优先级（最小 priority_level）的结果集。
     * <p>
     * 优化前：最坏 4 次串行 DB 调用；优化后：固定 1 次 DB 调用。
     */
    private List<Long> resolveAllowedStatusIds(Long projectId, String issueType,
                                               List<Long> roleIds, Long oldStatusId,
                                               boolean isAuthor, boolean isAssignee) {
        List<Map<String, Object>> results = transitionMapper.findAllowedNewStatusIdsWithPriority(
                projectId, issueType, roleIds, oldStatusId, isAuthor, isAssignee);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // 找到最高优先级（最小 priority_level 值）
        int minPriority = results.stream()
                .mapToInt(row -> ((Number) row.get("priority_level")).intValue())
                .min()
                .orElse(Integer.MAX_VALUE);

        // 筛选该优先级层的所有 new_status_id
        List<Long> statusIds = results.stream()
                .filter(row -> ((Number) row.get("priority_level")).intValue() == minPriority)
                .map(row -> ((Number) row.get("new_status_id")).longValue())
                .distinct()
                .toList();

        log.debug("Workflow transition resolved at Level {} (single query): {} statuses",
                minPriority, statusIds.size());
        return statusIds;
    }

    /**
     * 检查状态转换是否合法
     */
    public boolean isTransitionAllowed(Issue issue, Long newStatusId, Long userId) {
        List<IssueStatus> available = getAvailableTransitions(issue, userId);
        return available.stream().anyMatch(s -> s.getId().equals(newStatusId));
    }

    /**
     * 获取项目的工作流转换矩阵（用于编辑器展示）。
     * 支持按 author/assignee 模式筛选。
     *
     * @param author  null=不筛选, false=Normal模式, true=Author模式
     * @param assignee null=不筛选, false=Normal模式, true=Assignee模式
     */
    public List<WorkflowTransition> getTransitionMatrix(Long projectId, String issueType,
                                                        Long roleId, Boolean author, Boolean assignee) {
        LambdaQueryWrapper<WorkflowTransition> wrapper = new LambdaQueryWrapper<>();

        if (projectId != null) {
            wrapper.and(w -> w.eq(WorkflowTransition::getProjectId, projectId)
                    .or().isNull(WorkflowTransition::getProjectId));
        } else {
            wrapper.isNull(WorkflowTransition::getProjectId);
        }

        if (issueType != null && !issueType.isBlank()) {
            wrapper.and(w -> w.eq(WorkflowTransition::getIssueType, issueType)
                    .or().eq(WorkflowTransition::getIssueType, "*"));
        }

        if (roleId != null) {
            wrapper.eq(WorkflowTransition::getRoleId, roleId);
        }

        // author/assignee 模式筛选
        if (author != null) {
            wrapper.eq(WorkflowTransition::getAuthor, author);
        }
        if (assignee != null) {
            wrapper.eq(WorkflowTransition::getAssignee, assignee);
        }

        return transitionMapper.selectList(wrapper);
    }

    /**
     * 获取项目的工作流转换矩阵（向后兼容旧接口，不筛选 author/assignee）
     */
    public List<WorkflowTransition> getTransitionMatrix(Long projectId, String issueType, Long roleId) {
        return getTransitionMatrix(projectId, issueType, roleId, null, null);
    }

    /**
     * 获取工作流转换矩阵 + 版本号（用于编辑器，支持乐观锁）
     */
    public WorkflowMatrixVO getTransitionMatrixWithVersion(Long projectId, String issueType,
                                                           Long roleId, Boolean author, Boolean assignee) {
        List<WorkflowTransition> transitions = getTransitionMatrix(projectId, issueType, roleId, author, assignee);
        List<WorkflowTransitionVO> voList = workflowConverter.toVOList(transitions);

        // 获取版本号（仅当明确了 roleId + author/assignee 模式时才有意义）
        Integer version = null;
        if (roleId != null && author != null && assignee != null) {
            boolean effectiveAuthor = Boolean.TRUE.equals(author);
            boolean effectiveAssignee = Boolean.TRUE.equals(assignee);
            String effectiveIssueType = (issueType != null && !issueType.isBlank()) ? issueType : "*";
            version = getVersionNumber(projectId, effectiveIssueType, roleId, effectiveAuthor, effectiveAssignee);
        }

        WorkflowMatrixVO matrix = new WorkflowMatrixVO();
        matrix.setTransitions(voList);
        matrix.setVersion(version);
        return matrix;
    }

    /**
     * 查询当前版本号
     */
    private Integer getVersionNumber(Long projectId, String issueType, Long roleId,
                                     boolean author, boolean assignee) {
        LambdaQueryWrapper<WorkflowVersion> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(WorkflowVersion::getProjectId, projectId);
        } else {
            wrapper.isNull(WorkflowVersion::getProjectId);
        }
        wrapper.eq(WorkflowVersion::getIssueType, issueType);
        wrapper.eq(WorkflowVersion::getRoleId, roleId);
        wrapper.eq(WorkflowVersion::getAuthor, author);
        wrapper.eq(WorkflowVersion::getAssignee, assignee);

        WorkflowVersion record = versionMapper.selectOne(wrapper);
        return record != null ? record.getVersion() : null;
    }

    /**
     * 批量更新工作流转换矩阵（替换指定项目+类型+角色+模式的所有规则）
     * 
     * @param author  规则的 author 标记（Normal模式=false, Author模式=true）
     * @param assignee 规则的 assignee 标记（Normal模式=false, Assignee模式=true）
     */
    @Transactional
    public void updateTransitionMatrix(Long projectId, String issueType, Long roleId,
                                       Boolean author, Boolean assignee,
                                       List<WorkflowTransition> transitions) {
        boolean effectiveAuthor = Boolean.TRUE.equals(author);
        boolean effectiveAssignee = Boolean.TRUE.equals(assignee);

        // 查询旧规则（用于 diff 计算）
        LambdaQueryWrapper<WorkflowTransition> oldWrapper = buildDeleteWrapper(
                projectId, issueType, roleId, effectiveAuthor, effectiveAssignee);
        List<WorkflowTransition> oldTransitions = transitionMapper.selectList(oldWrapper);

        // 删除旧规则
        LambdaQueryWrapper<WorkflowTransition> deleteWrapper = buildDeleteWrapper(
                projectId, issueType, roleId, effectiveAuthor, effectiveAssignee);
        transitionMapper.delete(deleteWrapper);

        // 插入新规则
        for (WorkflowTransition t : transitions) {
            t.setProjectId(projectId);
            t.setIssueType(issueType != null ? issueType : "*");
            t.setRoleId(roleId);
            t.setAuthor(effectiveAuthor);
            t.setAssignee(effectiveAssignee);
            transitionMapper.insert(t);
        }

        // 计算 diff 并记录审计日志
        recordDetailedActivity(projectId, issueType, roleId, effectiveAuthor, effectiveAssignee,
                oldTransitions, transitions);
    }

    /**
     * 构建删除条件 Wrapper
     */
    private LambdaQueryWrapper<WorkflowTransition> buildDeleteWrapper(
            Long projectId, String issueType, Long roleId,
            boolean author, boolean assignee) {
        LambdaQueryWrapper<WorkflowTransition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(WorkflowTransition::getProjectId, projectId);
        } else {
            wrapper.isNull(WorkflowTransition::getProjectId);
        }
        wrapper.eq(WorkflowTransition::getIssueType, issueType != null ? issueType : "*");
        wrapper.eq(WorkflowTransition::getRoleId, roleId);
        wrapper.eq(WorkflowTransition::getAuthor, author);
        wrapper.eq(WorkflowTransition::getAssignee, assignee);
        return wrapper;
    }

    /**
     * 从 DTO 批量更新工作流转换矩阵（含乐观锁版本校验）
     */
    @Transactional
    public void updateTransitionMatrix(Long projectId, UpdateWorkflowDTO dto) {
        boolean effectiveAuthor = Boolean.TRUE.equals(dto.getAuthor());
        boolean effectiveAssignee = Boolean.TRUE.equals(dto.getAssignee());
        String effectiveIssueType = dto.getIssueType() != null ? dto.getIssueType() : "*";

        // 乐观锁版本校验
        if (dto.getVersion() != null) {
            checkAndIncrementVersion(projectId, effectiveIssueType, dto.getRoleId(),
                    effectiveAuthor, effectiveAssignee, dto.getVersion());
        } else {
            // 客户端未传 version（向后兼容旧客户端），仅递增版本号不做校验
            ensureAndIncrementVersion(projectId, effectiveIssueType, dto.getRoleId(),
                    effectiveAuthor, effectiveAssignee);
        }

        List<WorkflowTransition> transitions = dto.getTransitions().stream()
                .filter(t -> Boolean.TRUE.equals(t.getAllowed()))
                .map(t -> {
                    WorkflowTransition wt = new WorkflowTransition();
                    wt.setOldStatusId(t.getFrom());
                    wt.setNewStatusId(t.getTo());
                    return wt;
                })
                .toList();

        updateTransitionMatrix(projectId, dto.getIssueType(), dto.getRoleId(),
                dto.getAuthor(), dto.getAssignee(), transitions);
    }

    /**
     * 乐观锁校验：CAS 更新版本号，失败则说明有并发冲突
     */
    private void checkAndIncrementVersion(Long projectId, String issueType, Long roleId,
                                          boolean author, boolean assignee,
                                          Integer expectedVersion) {
        WorkflowVersion versionRecord = findOrCreateVersionRecord(projectId, issueType, roleId, author, assignee);

        int updated = versionMapper.incrementVersionCAS(
                versionRecord.getId(), expectedVersion, SecurityUtils.getCurrentUserId());

        if (updated == 0) {
            throw new BusinessException(ErrorCode.WORKFLOW_VERSION_CONFLICT,
                    "工作流已被其他人修改（当前版本: " + versionRecord.getVersion()
                            + "，期望版本: " + expectedVersion + "），请刷新后重试");
        }
    }

    /**
     * 向后兼容：客户端未传版本号时，直接递增版本（不做冲突检测）
     */
    private void ensureAndIncrementVersion(Long projectId, String issueType, Long roleId,
                                           boolean author, boolean assignee) {
        WorkflowVersion versionRecord = findOrCreateVersionRecord(projectId, issueType, roleId, author, assignee);
        versionMapper.incrementVersionCAS(
                versionRecord.getId(), versionRecord.getVersion(), SecurityUtils.getCurrentUserId());
    }

    /**
     * 查找版本记录，不存在则创建（初始版本=1）
     */
    private WorkflowVersion findOrCreateVersionRecord(Long projectId, String issueType,
                                                      Long roleId, boolean author, boolean assignee) {
        LambdaQueryWrapper<WorkflowVersion> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(WorkflowVersion::getProjectId, projectId);
        } else {
            wrapper.isNull(WorkflowVersion::getProjectId);
        }
        wrapper.eq(WorkflowVersion::getIssueType, issueType);
        wrapper.eq(WorkflowVersion::getRoleId, roleId);
        wrapper.eq(WorkflowVersion::getAuthor, author);
        wrapper.eq(WorkflowVersion::getAssignee, assignee);

        WorkflowVersion record = versionMapper.selectOne(wrapper);
        if (record == null) {
            record = new WorkflowVersion();
            record.setProjectId(projectId);
            record.setIssueType(issueType);
            record.setRoleId(roleId);
            record.setAuthor(author);
            record.setAssignee(assignee);
            record.setVersion(1);
            record.setUpdatedAt(LocalDateTime.now());
            record.setUpdatedBy(SecurityUtils.getCurrentUserId());
            versionMapper.insert(record);
        }
        return record;
    }

    /**
     * 获取项目级角色列表（用于工作流编辑器筛选下拉）
     */
    public List<RoleVO> listProjectRoles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleType, "project")
                        .orderByAsc(SysRole::getSortOrder)
        );
        return roleConverter.toVOList(roles);
    }

    /**
     * 获取当前用户在指定项目中可以发起状态转换的源状态 ID 集合。
     * 用于看板等场景预判哪些卡片可拖拽（基于状态维度）。
     * <p>
     * 使用 2 级优先级覆盖语义：
     * <ol>
     *   <li>project_id = X（项目级规则）</li>
     *   <li>project_id IS NULL（全局规则）</li>
     * </ol>
     * 注意：此方法不涉及 issue_type 维度（看板是项目级视图，不按类型区分）。
     * 也不区分 author/assignee（看板预判使用所有转换规则的并集）。
     */
    public Set<Long> getTransitionableSourceStatuses(Long projectId, Long userId) {
        // 系统管理员：使用 project_admin 规则
        if (permissionService.isSystemAdmin(userId)) {
            return resolveTransitionableSourceStatuses(projectId, List.of(PROJECT_ADMIN_ROLE_ID));
        }

        // 获取用户在项目中的角色
        List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(userId, projectId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }

        return resolveTransitionableSourceStatuses(projectId, roleIds);
    }

    /**
     * 2 级优先级解析可发起转换的源状态。
     * 有项目级规则时仅用项目级，无则 fallback 到全局。
     */
    private Set<Long> resolveTransitionableSourceStatuses(Long projectId, List<Long> roleIds) {
        // Level 1: 项目级规则
        List<Long> ids = transitionMapper.findTransitionableSourceStatusIdsExact(projectId, roleIds);
        if (!ids.isEmpty()) {
            return new HashSet<>(ids);
        }

        // Level 2: 全局规则
        ids = transitionMapper.findTransitionableSourceStatusIdsExact(null, roleIds);
        return new HashSet<>(ids);
    }

    /**
     * 获取系统中已使用的工单类型列表
     * 返回数据库中 issue 表的 distinct issue_type 值 + 预定义类型
     */
    public List<String> listIssueTypes() {
        // 预定义的基础类型（与系统内置类型一致）
        List<String> baseTypes = List.of("Bug", "Task", "Feature", "Epic");

        // 从 issue 表查询所有已使用的 issue_type（排除已删除的）
        List<Object> dbTypes = issueMapper.selectObjs(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getIssueType)
                        .isNull(Issue::getDeletedAt)
                        .groupBy(Issue::getIssueType)
        );

        // 合并：预定义 + 已使用（去重）
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(baseTypes);
        for (Object obj : dbTypes) {
            if (obj != null) {
                merged.add(obj.toString());
            }
        }
        return List.copyOf(merged);
    }

    /**
     * 检查指定状态在某个 issueType 的工作流图中是否存在。
     * 即该状态是否作为任何转换规则的 old_status 或 new_status 出现。
     * 用于类型变更后判断当前状态在新类型下是否仍然可达。
     */
    public boolean isStatusInWorkflow(Long projectId, String issueType, Long statusId) {
        int count = transitionMapper.countStatusInWorkflow(projectId, issueType, statusId);
        return count > 0;
    }

    /**
     * 获取系统默认状态（is_default = true 的状态）
     */
    public IssueStatus getDefaultStatus() {
        return statusMapper.selectOne(
                new LambdaQueryWrapper<IssueStatus>().eq(IssueStatus::getIsDefault, true));
    }

    /**
     * 分页查询工作流变更历史
     */
    public Page<WorkflowActivity> listActivities(WorkflowActivityQuery query) {
        LambdaQueryWrapper<WorkflowActivity> wrapper = new LambdaQueryWrapper<>();

        if (query.getProjectId() != null) {
            if (WorkflowScope.isGlobal(query.getProjectId())) {
                wrapper.isNull(WorkflowActivity::getProjectId);
            } else {
                wrapper.eq(WorkflowActivity::getProjectId, query.getProjectId());
            }
        }

        if (query.getUserId() != null) {
            wrapper.eq(WorkflowActivity::getUserId, query.getUserId());
        }

        if (query.getStartDate() != null) {
            wrapper.ge(WorkflowActivity::getCreatedAt, query.getStartDate().atStartOfDay());
        }

        if (query.getEndDate() != null) {
            wrapper.lt(WorkflowActivity::getCreatedAt, query.getEndDate().plusDays(1).atStartOfDay());
        }

        wrapper.orderByDesc(WorkflowActivity::getCreatedAt);

        return activityMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 记录详细的工作流变更审计日志（含 diff 明细）
     */
    private void recordDetailedActivity(Long projectId, String issueType, Long roleId,
                                        boolean author, boolean assignee,
                                        List<WorkflowTransition> oldTransitions,
                                        List<WorkflowTransition> newTransitions) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();

            // 计算 diff：旧的中不在新的中 = removed；新的中不在旧的中 = added
            Set<String> oldKeys = oldTransitions.stream()
                    .map(t -> t.getOldStatusId() + "->" + t.getNewStatusId())
                    .collect(Collectors.toSet());
            Set<String> newKeys = newTransitions.stream()
                    .map(t -> t.getOldStatusId() + "->" + t.getNewStatusId())
                    .collect(Collectors.toSet());

            Set<String> addedKeys = new HashSet<>(newKeys);
            addedKeys.removeAll(oldKeys);
            Set<String> removedKeys = new HashSet<>(oldKeys);
            removedKeys.removeAll(newKeys);

            // 如果没有实际变更，不记录
            if (addedKeys.isEmpty() && removedKeys.isEmpty()) {
                return;
            }

            // 获取状态名称映射
            Map<Long, String> statusNameMap = getStatusNameMap();

            // 构建人类可读的变更明细
            List<Map<String, String>> addedList = addedKeys.stream()
                    .map(key -> buildTransitionItem(key, statusNameMap))
                    .toList();
            List<Map<String, String>> removedList = removedKeys.stream()
                    .map(key -> buildTransitionItem(key, statusNameMap))
                    .toList();

            // 构建摘要（含模式信息）
            String modeLabel = getModeLabel(author, assignee);
            String summary = buildSummary(addedList, removedList, statusNameMap, roleId, modeLabel);

            // 构建 JSON details
            Map<String, Object> detailsMap = new java.util.LinkedHashMap<>();
            detailsMap.put("mode", modeLabel);
            if (!addedList.isEmpty()) {
                detailsMap.put("added", addedList);
            }
            if (!removedList.isEmpty()) {
                detailsMap.put("removed", removedList);
            }
            String detailsJson = objectMapper.writeValueAsString(detailsMap);

            WorkflowActivity activity = new WorkflowActivity();
            activity.setProjectId(projectId);
            activity.setIssueType(issueType != null ? issueType : "*");
            activity.setRoleId(roleId);
            activity.setUserId(userId);
            activity.setAction("workflow_updated");
            activity.setOldValue(oldTransitions.size() + " transitions");
            activity.setNewValue(newTransitions.size() + " transitions");
            activity.setSummary(summary);
            activity.setDetails(detailsJson);
            activity.setCreatedAt(LocalDateTime.now());
            activityMapper.insert(activity);
        } catch (Exception e) {
            // 审计日志写入失败不应中断主流程
            log.warn("Failed to record workflow activity: {}", e.getMessage());
        }
    }

    /**
     * 获取模式标签
     */
    private String getModeLabel(boolean author, boolean assignee) {
        if (author) return "Author";
        if (assignee) return "Assignee";
        return "Normal";
    }

    /**
     * 获取所有状态的 ID→显示名称映射（优先 displayName，fallback 到 name）
     */
    private Map<Long, String> getStatusNameMap() {
        List<IssueStatus> allStatuses = statusMapper.selectList(new LambdaQueryWrapper<>());
        return allStatuses.stream()
                .collect(Collectors.toMap(IssueStatus::getId, IssueStatus::getLocalizedName));
    }

    /**
     * 从 "fromId->toId" 格式的 key 构建转换明细项
     */
    private Map<String, String> buildTransitionItem(String key, Map<Long, String> statusNameMap) {
        String[] parts = key.split("->");
        Long fromId = Long.parseLong(parts[0]);
        Long toId = Long.parseLong(parts[1]);
        Map<String, String> item = new java.util.LinkedHashMap<>();
        item.put("fromStatusId", parts[0]);
        item.put("toStatusId", parts[1]);
        item.put("fromStatus", statusNameMap.getOrDefault(fromId, "Unknown"));
        item.put("toStatus", statusNameMap.getOrDefault(toId, "Unknown"));
        return item;
    }

    /**
     * 构建人类可读的变更摘要
     */
    private String buildSummary(List<Map<String, String>> addedList,
                                List<Map<String, String>> removedList,
                                Map<Long, String> statusNameMap, Long roleId,
                                String modeLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(modeLabel).append("] ");
        if (!addedList.isEmpty()) {
            sb.append("新增 ").append(addedList.size()).append(" 条转换");
            if (addedList.size() <= 2) {
                sb.append("（");
                sb.append(addedList.stream()
                        .map(item -> item.get("fromStatus") + "→" + item.get("toStatus"))
                        .collect(Collectors.joining("，")));
                sb.append("）");
            }
        }
        if (!removedList.isEmpty()) {
            if (addedList.isEmpty()) {
                // 没有新增时不需要分号
            } else {
                sb.append("；");
            }
            sb.append("删除 ").append(removedList.size()).append(" 条转换");
            if (removedList.size() <= 2) {
                sb.append("（");
                sb.append(removedList.stream()
                        .map(item -> item.get("fromStatus") + "→" + item.get("toStatus"))
                        .collect(Collectors.joining("，")));
                sb.append("）");
            }
        }
        return sb.toString();
    }
}

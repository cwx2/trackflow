package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.trackflow.workflow.dto.UpdateWorkflowDTO;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.WorkflowActivityMapper;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowTransitionMapper transitionMapper;
    private final WorkflowActivityMapper activityMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueMapper issueMapper;
    private final ProjectMemberMapper memberMapper;
    private final SysRoleMapper roleMapper;
    private final RoleConverter roleConverter;
    private final PermissionService permissionService;

    /**
     * 需要检查工单所有权的角色 code 集合。
     * 这些角色只能修改分配给自己或由自己创建/报告的工单状态。
     * project_admin、tech_lead、product_manager 可修改任意工单。
     * tester 不在此集合中——测试人员的核心职责就是验证他人开发的工单，
     * 其权限已通过 workflow_transition 表的转换规则充分约束。
     */
    private static final Set<String> OWNERSHIP_REQUIRED_ROLES = Set.of("developer");

    /**
     * project_admin 的角色 ID（来自 V2__seed_roles.sql 种子数据，ID 固定为 2）。
     * 系统管理员无项目角色时，使用此角色的工作流规则。
     */
    private static final String PROJECT_ADMIN_ROLE_ID = "2";

    /**
     * 获取当前用户对指定 Issue 可以转换到的目标状态列表。
     * 
     * 所有权规则：
     * - system_admin：全局权限，不受限制
     * - project_admin / tech_lead / product_manager：可修改项目内任意工单状态
     * - tester：可修改任意工单状态（受 workflow_transition 规则约束可用转换路径）
     * - developer：只能修改分配给自己或由自己报告的工单状态
     * - observer：无状态变更权限（workflow_transition 表中无对应规则）
     */
    public List<IssueStatus> getAvailableTransitions(Issue issue, Long userId) {
        // 系统管理员直接跳过所有权检查（由全局权限保障）
        if (permissionService.isSystemAdmin(userId)) {
            // 系统管理员使用 project_admin 的工作流规则
            return getTransitionsForRoles(issue, PROJECT_ADMIN_ROLE_ID);
        }

        // 获取用户在项目中的角色
        List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(userId, issue.getProjectId());
        if (roleIds.isEmpty()) {
            return List.of();
        }

        // 检查用户是否需要所有权校验
        if (requiresOwnershipCheck(roleIds)) {
            // developer 需要是工单的 assignee 或 reporter 才能变更状态
            if (!isIssueOwner(issue, userId)) {
                log.debug("User {} denied status transition on issue {} - not assignee or reporter",
                        userId, issue.getId());
                return List.of();
            }
        }

        String roleIdsStr = roleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return getTransitionsForRoles(issue, roleIdsStr);
    }

    /**
     * 检查用户是否为工单的"所有者"（assignee 或 reporter）
     */
    public boolean isIssueOwner(Issue issue, Long userId) {
        return Objects.equals(issue.getAssigneeId(), userId)
                || Objects.equals(issue.getReporterId(), userId)
                || Objects.equals(issue.getCreatedBy(), userId);
    }

    /**
     * 角色 ID → code 缓存。角色 code 为静态种子数据，几乎不变，可安全缓存。
     */
    private final Map<Long, String> roleCodeCache = new ConcurrentHashMap<>();

    /**
     * 判断用户的角色是否需要所有权校验。
     * 只要用户在该项目中拥有任何一个"管理级"角色（project_admin / tech_lead / product_manager），
     * 就不需要所有权校验。
     */
    private boolean requiresOwnershipCheck(List<Long> roleIds) {
        for (Long roleId : roleIds) {
            String code = roleCodeCache.computeIfAbsent(roleId, id -> {
                SysRole role = roleMapper.selectById(id);
                return role != null ? role.getCode() : "";
            });
            if (!OWNERSHIP_REQUIRED_ROLES.contains(code)) {
                return false;
            }
        }
        // 所有角色都是需要检查所有权的角色
        return true;
    }

    /**
     * 根据角色 ID 列表查询可用的状态转换
     */
    private List<IssueStatus> getTransitionsForRoles(Issue issue, String roleIdsStr) {
        // 查询允许的目标状态
        List<Long> allowedStatusIds = transitionMapper.findAllowedNewStatusIds(
                issue.getProjectId(), issue.getIssueType(), roleIdsStr, issue.getStatusId()
        );

        if (allowedStatusIds.isEmpty()) {
            // Fallback: 尝试不带 projectId（全局规则）
            allowedStatusIds = transitionMapper.findAllowedNewStatusIds(
                    null, issue.getIssueType(), roleIdsStr, issue.getStatusId()
            );
        }

        if (allowedStatusIds.isEmpty()) {
            return List.of();
        }

        return statusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>().in(IssueStatus::getId, allowedStatusIds)
                        .orderByAsc(IssueStatus::getSortOrder)
        );
    }

    /**
     * 检查状态转换是否合法
     */
    public boolean isTransitionAllowed(Issue issue, Long newStatusId, Long userId) {
        List<IssueStatus> available = getAvailableTransitions(issue, userId);
        return available.stream().anyMatch(s -> s.getId().equals(newStatusId));
    }

    /**
     * 获取项目的工作流转换矩阵
     */
    public List<WorkflowTransition> getTransitionMatrix(Long projectId, String issueType, Long roleId) {
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

        return transitionMapper.selectList(wrapper);
    }

    /**
     * 批量更新工作流转换矩阵（替换指定项目+类型+角色的所有规则）
     */
    @Transactional
    public void updateTransitionMatrix(Long projectId, String issueType, Long roleId,
                                       List<WorkflowTransition> transitions) {
        // 统计旧规则数量（用于审计日志）
        LambdaQueryWrapper<WorkflowTransition> countWrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            countWrapper.eq(WorkflowTransition::getProjectId, projectId);
        } else {
            countWrapper.isNull(WorkflowTransition::getProjectId);
        }
        countWrapper.eq(WorkflowTransition::getIssueType, issueType != null ? issueType : "*");
        countWrapper.eq(WorkflowTransition::getRoleId, roleId);
        long oldCount = transitionMapper.selectCount(countWrapper);

        // 删除旧规则
        LambdaQueryWrapper<WorkflowTransition> deleteWrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            deleteWrapper.eq(WorkflowTransition::getProjectId, projectId);
        } else {
            deleteWrapper.isNull(WorkflowTransition::getProjectId);
        }
        deleteWrapper.eq(WorkflowTransition::getIssueType, issueType != null ? issueType : "*");
        deleteWrapper.eq(WorkflowTransition::getRoleId, roleId);
        transitionMapper.delete(deleteWrapper);

        // 插入新规则
        for (WorkflowTransition t : transitions) {
            t.setProjectId(projectId);
            t.setIssueType(issueType != null ? issueType : "*");
            t.setRoleId(roleId);
            transitionMapper.insert(t);
        }

        // 记录审计日志
        recordActivity(projectId, issueType, roleId,
                "workflow_updated",
                String.valueOf(oldCount) + " transitions",
                String.valueOf(transitions.size()) + " transitions");
    }

    /**
     * 从 DTO 批量更新工作流转换矩阵
     */
    @Transactional
    public void updateTransitionMatrix(Long projectId, UpdateWorkflowDTO dto) {
        List<WorkflowTransition> transitions = dto.getTransitions().stream()
                .filter(t -> Boolean.TRUE.equals(t.getAllowed()))
                .map(t -> {
                    WorkflowTransition wt = new WorkflowTransition();
                    wt.setOldStatusId(t.getFrom());
                    wt.setNewStatusId(t.getTo());
                    return wt;
                })
                .toList();

        updateTransitionMatrix(projectId, dto.getIssueType(), dto.getRoleId(), transitions);
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
     *
     * 注意：对于 developer 角色，还有所有权检查（只能改自己的工单），
     * 这里仅返回工作流规则维度的判断，不考虑所有权。
     * 前端应将此作为"必要条件"而非"充分条件"。
     */
    public Set<Long> getTransitionableSourceStatuses(Long projectId, Long userId) {
        // 系统管理员：返回所有状态（使用 project_admin 规则）
        if (permissionService.isSystemAdmin(userId)) {
            List<Long> ids = transitionMapper.findTransitionableSourceStatusIds(projectId, PROJECT_ADMIN_ROLE_ID);
            if (ids.isEmpty()) {
                // Fallback: 全局规则
                ids = transitionMapper.findTransitionableSourceStatusIds(null, PROJECT_ADMIN_ROLE_ID);
            }
            return new HashSet<>(ids);
        }

        // 获取用户在项目中的角色
        List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(userId, projectId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }

        String roleIdsStr = roleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Long> ids = transitionMapper.findTransitionableSourceStatusIds(projectId, roleIdsStr);
        if (ids.isEmpty()) {
            // Fallback: 全局规则
            ids = transitionMapper.findTransitionableSourceStatusIds(null, roleIdsStr);
        }
        return new HashSet<>(ids);
    }

    /**
     * 获取系统中已使用的工单类型列表
     * 返回数据库中 issue 表的 distinct issue_type 值 + 预定义类型
     */
    public List<String> listIssueTypes() {
        // 预定义的基础类型
        List<String> baseTypes = List.of("Bug", "Task", "Feature");

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
     * 记录工作流变更审计日志
     */
    private void recordActivity(Long projectId, String issueType, Long roleId,
                                String action, String oldValue, String newValue) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            WorkflowActivity activity = new WorkflowActivity();
            activity.setProjectId(projectId);
            activity.setIssueType(issueType != null ? issueType : "*");
            activity.setRoleId(roleId);
            activity.setUserId(userId);
            activity.setAction(action);
            activity.setOldValue(oldValue);
            activity.setNewValue(newValue);
            activity.setCreatedAt(LocalDateTime.now());
            activityMapper.insert(activity);
        } catch (Exception e) {
            // 审计日志写入失败不应中断主流程
            log.warn("Failed to record workflow activity: {}", e.getMessage());
        }
    }
}

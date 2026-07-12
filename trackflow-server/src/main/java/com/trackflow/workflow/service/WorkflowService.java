package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
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

    /**
     * 获取当前用户对指定 Issue 可以转换到的目标状态列表
     */
    public List<IssueStatus> getAvailableTransitions(Issue issue, Long userId) {
        // 获取用户在项目中的角色
        List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(userId, issue.getProjectId());
        if (roleIds.isEmpty()) {
            return List.of();
        }

        String roleIdsStr = roleIds.stream().map(String::valueOf).collect(Collectors.joining(","));

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

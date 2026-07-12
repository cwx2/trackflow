package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.workflow.dto.UpdateWorkflowDTO;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowTransitionMapper transitionMapper;
    private final IssueStatusMapper statusMapper;
    private final ProjectMemberMapper memberMapper;

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
}

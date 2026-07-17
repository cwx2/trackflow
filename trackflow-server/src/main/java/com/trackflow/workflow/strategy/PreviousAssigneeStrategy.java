package com.trackflow.workflow.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 前一任负责人策略 —— 查找 issue_activity 中最近一次 assignee 变更的旧值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreviousAssigneeStrategy implements AssignmentStrategy {

    private final IssueActivityMapper issueActivityMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public String getKey() {
        return "previous_assignee";
    }

    @Override
    public Long resolve(Issue issue, ActionConfig config, Long projectId) {
        String oldValue = issueActivityMapper.selectPreviousAssignee(issue.getId());

        if (oldValue == null || oldValue.isBlank()) {
            log.warn("[PreviousAssigneeStrategy] Issue {} 无历史负责人记录", issue.getId());
            return null;
        }

        Long previousAssigneeId;
        try {
            previousAssigneeId = Long.parseLong(oldValue);
        } catch (NumberFormatException e) {
            log.warn("[PreviousAssigneeStrategy] 无法解析 old_value='{}' 为用户 ID, issue={}",
                    oldValue, issue.getId());
            return null;
        }

        // 验证前一任负责人是否仍为活跃项目成员
        boolean isMember = projectMemberMapper.exists(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, previousAssigneeId)
        );

        if (!isMember) {
            log.warn("[PreviousAssigneeStrategy] 前一任负责人 {} 不再是项目 {} 的活跃成员，跳过分配",
                    previousAssigneeId, projectId);
            return null;
        }

        return previousAssigneeId;
    }
}

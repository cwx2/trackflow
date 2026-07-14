package com.trackflow.workflow.strategy;

import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueActivityMapper;
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

        try {
            return Long.parseLong(oldValue);
        } catch (NumberFormatException e) {
            log.warn("[PreviousAssigneeStrategy] 无法解析 old_value='{}' 为用户 ID, issue={}",
                    oldValue, issue.getId());
            return null;
        }
    }
}

package com.trackflow.workflow.strategy;

import com.trackflow.issue.entity.Issue;
import com.trackflow.workflow.dto.ActionConfig;

/**
 * 分配策略接口 —— 每种分配算法实现此接口。
 * 引擎按 action_config.strategy 字段匹配对应策略实现。
 */
public interface AssignmentStrategy {

    /**
     * 策略标识符，与 action_config.strategy 字段值对应。
     * 例如: specific_user, role_based, previous_assignee, reporter, project_lead
     */
    String getKey();

    /**
     * 解析目标用户 ID。
     *
     * @param issue     当前 Issue 实体（状态已更新）
     * @param config    从 transition_action.action_config 解析出的配置对象
     * @param projectId 项目 ID，用于成员查询
     * @return 目标用户 ID，无法解析时返回 null
     */
    Long resolve(Issue issue, ActionConfig config, Long projectId);
}

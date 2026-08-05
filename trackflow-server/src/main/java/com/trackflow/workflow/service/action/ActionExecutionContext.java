package com.trackflow.workflow.service.action;

import com.trackflow.issue.entity.Issue;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作执行上下文 — 跨多个 action block 共享状态。
 * <p>
 * 在一次规则执行中，多个动作按顺序执行，部分动作需要引用前序动作的产物
 * （如 link_issue 引用 create_issue 的结果）。通过上下文对象传递这些共享状态。
 *
 * @author TrackFlow
 * @since 1.0
 */
public class ActionExecutionContext {

    /**
     * 本次规则执行中 create_issue / copy_issue 动作产生的新工单列表。
     * link_issue 可通过 "from_block:N" 引用。
     */
    private final List<Issue> createdIssues = new ArrayList<>();

    public List<Issue> getCreatedIssues() {
        return createdIssues;
    }

    public void addCreatedIssue(Issue issue) {
        createdIssues.add(issue);
    }
}

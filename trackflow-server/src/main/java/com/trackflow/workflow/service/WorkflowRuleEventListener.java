package com.trackflow.workflow.service;

import com.trackflow.common.event.WorkflowRuleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听 WorkflowRuleEvent 并委托给 WorkflowRuleEngine 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRuleEventListener {

    private final WorkflowRuleEngine ruleEngine;

    @EventListener
    public void onIssueCreated(WorkflowRuleEvent.IssueCreated event) {
        ruleEngine.fireOnCreate(event.issue());
    }

    @EventListener
    public void onFieldChanged(WorkflowRuleEvent.FieldChanged event) {
        ruleEngine.fireOnFieldChanged(event.issue(), event.changedField(), event.oldValue());
    }
}

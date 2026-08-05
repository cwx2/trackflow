package com.trackflow.workflow.service;

import com.trackflow.common.event.WorkflowRuleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 WorkflowRuleEvent 并委托给 WorkflowRuleEngine 异步执行。
 * <p>
 * 设计要点：
 * - 使用 @TransactionalEventListener(AFTER_COMMIT) 确保规则仅在工单事务成功提交后触发
 * - 使用 @Async("ruleEngineExecutor") 异步执行，避免阻塞 API 响应
 * - 全覆盖 try-catch，任何规则异常不影响主业务（事务已提交）
 * - 事件 payload 仅传递 ID，规则引擎执行时从 DB 重新加载最新实体状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRuleEventListener {

    private final WorkflowRuleEngine ruleEngine;

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(WorkflowRuleEvent.IssueCreated event) {
        try {
            ruleEngine.fireOnCreate(event.issueId(), event.projectId());
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] on-create 规则执行失败: issueId={}, projectId={}, error={}",
                    event.issueId(), event.projectId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFieldChanged(WorkflowRuleEvent.FieldChanged event) {
        try {
            ruleEngine.fireOnFieldChanged(event.issueId(), event.projectId(), event.changedField(), event.oldValue());
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] on-field-changed 规则执行失败: issueId={}, field={}, error={}",
                    event.issueId(), event.changedField(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentAdded(WorkflowRuleEvent.CommentAdded event) {
        try {
            ruleEngine.fireOnCommentAdded(event.issueId(), event.projectId(), event.commentContent());
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] on-comment-added 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentAdded(WorkflowRuleEvent.AttachmentAdded event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "attachment_added");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] attachment_added 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentRemoved(WorkflowRuleEvent.AttachmentRemoved event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "attachment_removed");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] attachment_removed 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinkAdded(WorkflowRuleEvent.LinkAdded event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "link_added");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] link_added 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinkRemoved(WorkflowRuleEvent.LinkRemoved event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "link_removed");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] link_removed 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkItemAdded(WorkflowRuleEvent.WorkItemAdded event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "work_item_added");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] work_item_added 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkItemDeleted(WorkflowRuleEvent.WorkItemDeleted event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "work_item_deleted");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] work_item_deleted 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueResolved(WorkflowRuleEvent.IssueResolved event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "issue_resolved");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] issue_resolved 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }

    @Async("ruleEngineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUnresolved(WorkflowRuleEvent.IssueUnresolved event) {
        try {
            ruleEngine.fireOnEvent(event.issueId(), event.projectId(), "issue_unresolved");
        } catch (Exception e) {
            log.error("[WorkflowRuleEvent] issue_unresolved 规则执行失败: issueId={}, error={}",
                    event.issueId(), e.getMessage(), e);
        }
    }
}

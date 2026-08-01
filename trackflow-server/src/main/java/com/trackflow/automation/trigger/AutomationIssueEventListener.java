package com.trackflow.automation.trigger;

import com.trackflow.common.event.WorkflowRuleEvent;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.service.IssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 复用 TrackFlow 现有工单领域事件，生成自主 Agent 工作项。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationIssueEventListener {
    private final AutomationTriggerService triggerService;
    private final IssueService issueService;

    @EventListener
    public void onIssueCreated(WorkflowRuleEvent.IssueCreated event) {
        route("issue_created", event.issueId(), event.projectId(), null, null);
    }

    @EventListener
    public void onFieldChanged(WorkflowRuleEvent.FieldChanged event) {
        route("issue_changed", event.issueId(), event.projectId(),
                event.changedField(), event.oldValue());
    }

    private void route(String type, Long issueId, Long projectId,
                       String changedField, String oldValue) {
        try {
            Issue issue = issueService.getById(issueId);
            String eventKey = type + ":" + issueId + ":" + issue.getVersion()
                    + (changedField != null ? ":" + changedField : "");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", type);
            payload.put("issueId", issueId);
            payload.put("issueKey", issue.getIssueKey());
            payload.put("issueVersion", issue.getVersion());
            payload.put("changedField", changedField);
            payload.put("oldValue", oldValue);
            if ("status_id".equals(changedField)) {
                IssueActivity activity = issueService.getLastStatusChange(issueId);
                payload.put("source", activity != null && activity.getSource() != null
                        ? activity.getSource() : "manual");
            } else {
                payload.put("source", "manual");
            }
            triggerService.recordIssueEvent(type, issueId, projectId, eventKey, payload);
        } catch (Exception exception) {
            log.error("自动化工单事件入队失败: issueId={}, type={}", issueId, type, exception);
            throw new IllegalStateException("自动化工单事件无法写入可靠收件箱", exception);
        }
    }
}

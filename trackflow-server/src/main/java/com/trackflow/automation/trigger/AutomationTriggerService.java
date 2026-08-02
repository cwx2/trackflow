package com.trackflow.automation.trigger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.runtime.AutomationRuntimeCoordinator;
import com.trackflow.automation.trigger.entity.AutomationEventInbox;
import com.trackflow.automation.trigger.mapper.AutomationEventInboxMapper;
import com.trackflow.automation.workitem.AutomationWorkItemService;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将领域事件路由为幂等工作项。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationTriggerService {
    private final AutomationWorkflowMapper workflowMapper;
    private final AutomationWorkItemService workItemService;
    private final AutomationEventInboxMapper eventInboxMapper;
    private final ObjectMapper objectMapper;
    private final AutomationRuntimeCoordinator runtimeCoordinator;

    public void recordIssueEvent(String triggerType, Long issueId, Long projectId,
                                 String eventKey, Map<String, Object> eventPayload) {
        if (!runtimeCoordinator.shouldRunTrigger(triggerType)) return;
        long subscriberCount = workflowMapper.selectCount(
                new LambdaQueryWrapper<AutomationWorkflow>()
                        .eq(AutomationWorkflow::getStatus, "published")
                        .eq(AutomationWorkflow::getRuntimeEnabled, true)
                        .eq(AutomationWorkflow::getTriggerType, triggerType)
                        .and(query -> query.eq(AutomationWorkflow::getProjectId, projectId)
                                .or().isNull(AutomationWorkflow::getProjectId)));
        if (subscriberCount == 0) return;
        AutomationEventInbox event = new AutomationEventInbox();
        event.setEventType(triggerType);
        event.setEventKey(eventKey);
        event.setIssueId(issueId);
        event.setProjectId(projectId);
        event.setOccurredAt(LocalDateTime.now());
        event.setAvailableAt(LocalDateTime.now());
        event.setAttempt(0);
        event.setMaxAttempts(10);
        try {
            event.setPayload(objectMapper.writeValueAsString(eventPayload));
            eventInboxMapper.insert(event);
            runtimeCoordinator.wakeWorkersAfterCommit();
        } catch (DuplicateKeyException ignored) {
            log.debug("自动化领域事件已记录，忽略重复事件: {}", eventKey);
        } catch (Exception exception) {
            throw new IllegalStateException("自动化领域事件无法持久化", exception);
        }
    }

    public void processPendingEvents() {
        List<AutomationEventInbox> events = eventInboxMapper.selectList(
                new LambdaQueryWrapper<AutomationEventInbox>()
                        .isNull(AutomationEventInbox::getProcessedAt)
                        .le(AutomationEventInbox::getAvailableAt, LocalDateTime.now())
                        .orderByAsc(AutomationEventInbox::getOccurredAt)
                        .last("LIMIT 100"));
        for (AutomationEventInbox event : events) {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        event.getPayload(), new TypeReference<>() {});
                onIssueEvent(event.getEventType(), event.getIssueId(), event.getProjectId(),
                        event.getEventKey(), payload);
                AutomationEventInbox update = new AutomationEventInbox();
                update.setId(event.getId());
                update.setProcessedAt(LocalDateTime.now());
                update.setErrorMessage(null);
                eventInboxMapper.updateById(update);
            } catch (Exception exception) {
                int attempt = (event.getAttempt() != null ? event.getAttempt() : 0) + 1;
                int maxAttempts = event.getMaxAttempts() != null ? event.getMaxAttempts() : 10;
                AutomationEventInbox update = new AutomationEventInbox();
                update.setId(event.getId());
                update.setAttempt(attempt);
                update.setErrorMessage(trimError(exception));
                if (attempt >= maxAttempts) {
                    update.setProcessedAt(LocalDateTime.now());
                    log.error("自动化领域事件进入死信: eventKey={}", event.getEventKey(), exception);
                } else {
                    long delaySeconds = Math.min(3600L, 1L << Math.min(attempt, 12));
                    update.setAvailableAt(LocalDateTime.now().plusSeconds(delaySeconds));
                    log.warn("自动化领域事件路由失败，将重试: eventKey={}, attempt={}",
                            event.getEventKey(), attempt, exception);
                }
                eventInboxMapper.updateById(update);
            }
        }
    }

    public void onIssueEvent(String triggerType, Long issueId, Long projectId,
                             String eventKey, Map<String, Object> eventPayload) {
        List<AutomationWorkflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<AutomationWorkflow>()
                        .eq(AutomationWorkflow::getStatus, "published")
                        .eq(AutomationWorkflow::getRuntimeEnabled, true)
                        .eq(AutomationWorkflow::getTriggerType, triggerType)
                        .and(query -> query.eq(AutomationWorkflow::getProjectId, projectId)
                                .or().isNull(AutomationWorkflow::getProjectId)));
        for (AutomationWorkflow workflow : workflows) {
            if (!matchesConfig(workflow, eventPayload)) continue;
            Map<String, Object> payload = new LinkedHashMap<>(eventPayload);
            payload.put("triggerType", triggerType);
            payload.put("issueId", issueId);
            payload.put("projectId", projectId);
            workItemService.enqueue(workflow.getId(), issueId, eventKey, payload, 0);
        }
    }

    public void enqueueSchedule(AutomationWorkflow workflow, String slotKey,
                                Map<String, Object> payload) {
        workItemService.enqueue(workflow.getId(), null, slotKey, payload, 0);
    }

    public void enqueueWebhook(AutomationWorkflow workflow, String eventKey,
                               Map<String, Object> payload) {
        workItemService.enqueue(workflow.getId(), null, eventKey, payload, 0);
    }

    private boolean matchesConfig(AutomationWorkflow workflow, Map<String, Object> eventPayload) {
        if (!"issue_changed".equals(workflow.getTriggerType())) return true;
        try {
            Map<String, Object> config = workflow.getTriggerConfig() == null ? Map.of()
                    : objectMapper.readValue(workflow.getTriggerConfig(), new TypeReference<>() {});
            if ("automation".equals(eventPayload.get("source"))
                    && !Boolean.TRUE.equals(config.get("allowAutomationEvents"))) {
                return false;
            }
            Object fieldsValue = config.get("fields");
            if (!(fieldsValue instanceof List<?> fields) || fields.isEmpty()) return true;
            return fields.stream().map(String::valueOf)
                    .anyMatch(field -> field.equals(String.valueOf(eventPayload.get("changedField"))));
        } catch (Exception exception) {
            log.warn("忽略无效的自动化触发配置: workflowId={}", workflow.getId(), exception);
            return false;
        }
    }

    private String trimError(Exception exception) {
        String message = exception.getMessage() != null
                ? exception.getMessage() : exception.getClass().getSimpleName();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}

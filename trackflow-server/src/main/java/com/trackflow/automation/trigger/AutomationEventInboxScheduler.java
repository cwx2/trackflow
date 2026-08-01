package com.trackflow.automation.trigger;

import com.trackflow.common.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 集群单活地消费事件收件箱；失败事件按退避时间自动重试。 */
@Component
@RequiredArgsConstructor
public class AutomationEventInboxScheduler {
    private final DistributedLockService lockService;
    private final AutomationTriggerService triggerService;

    @Scheduled(fixedDelay = 5000)
    public void process() {
        lockService.executeWithLock("automation_event_inbox",
                triggerService::processPendingEvents);
    }
}

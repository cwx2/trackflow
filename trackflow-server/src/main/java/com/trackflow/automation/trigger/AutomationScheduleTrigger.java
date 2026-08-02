package com.trackflow.automation.trigger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.runtime.AutomationRuntimeCoordinator;
import com.trackflow.common.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 按每个已发布工作流自己的 cron 表达式产生调度工作项。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationScheduleTrigger {
    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private final AutomationWorkflowMapper workflowMapper;
    private final AutomationTriggerService triggerService;
    private final DistributedLockService distributedLockService;
    private final ObjectMapper objectMapper;
    private final AutomationRuntimeCoordinator runtimeCoordinator;

    @Scheduled(cron = "0 * * * * ?")
    public void tick() {
        if (!runtimeCoordinator.shouldRunTrigger("schedule")) return;
        distributedLockService.executeWithLock("automation_schedule_trigger", this::enqueueDueSchedules);
    }

    private void enqueueDueSchedules() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<AutomationWorkflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<AutomationWorkflow>()
                        .eq(AutomationWorkflow::getStatus, "published")
                        .eq(AutomationWorkflow::getRuntimeEnabled, true)
                        .eq(AutomationWorkflow::getTriggerType, "schedule"));
        for (AutomationWorkflow workflow : workflows) {
            try {
                Map<String, Object> config = objectMapper.readValue(
                        workflow.getTriggerConfig(), new TypeReference<>() {});
                String cron = normalizeCron(String.valueOf(config.getOrDefault("cron", "")));
                ZoneId zone = ZoneId.of(String.valueOf(config.getOrDefault("timezone", "Asia/Shanghai")));
                LocalDateTime zonedNow = LocalDateTime.now(zone).withSecond(0).withNano(0);
                LocalDateTime next = CronExpression.parse(cron).next(zonedNow.minusMinutes(1));
                if (next != null && next.equals(zonedNow)) {
                    String slot = "schedule:" + zonedNow.format(SLOT_FORMAT);
                    triggerService.enqueueSchedule(workflow, slot, Map.of(
                            "triggerType", "schedule", "scheduledAt", zonedNow.toString(),
                            "timezone", zone.getId()));
                }
            } catch (Exception exception) {
                log.warn("跳过无效调度配置: workflowId={}", workflow.getId(), exception);
            }
        }
    }

    private String normalizeCron(String cron) {
        return switch (cron.toLowerCase()) {
            case "hourly" -> "0 0 * * * *";
            case "daily" -> "0 0 9 * * *";
            case "weekly" -> "0 0 9 * * MON";
            default -> cron;
        };
    }
}

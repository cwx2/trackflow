package com.trackflow.automation.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.approval.entity.AutomationApproval;
import com.trackflow.automation.approval.mapper.AutomationApprovalMapper;
import com.trackflow.automation.runtime.AutomationRuntimeCoordinator;
import com.trackflow.common.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationApprovalExpiryScheduler {
    private final AutomationApprovalMapper mapper;
    private final AutomationApprovalService service;
    private final DistributedLockService lockService;
    private final AutomationRuntimeCoordinator runtimeCoordinator;

    @Scheduled(fixedDelay = 60000)
    public void expire() {
        if (!runtimeCoordinator.shouldRunWorkers()) return;
        lockService.executeWithLock("automation_approval_expiry", () -> {
            List<AutomationApproval> due = mapper.selectList(
                    new LambdaQueryWrapper<AutomationApproval>()
                            .eq(AutomationApproval::getStatus, "pending")
                            .le(AutomationApproval::getExpiresAt, LocalDateTime.now())
                            .last("LIMIT 100"));
            for (AutomationApproval approval : due) {
                try {
                    service.decide(approval.getId(), false, "审批已过期，自动拒绝");
                } catch (Exception exception) {
                    log.warn("自动过期审批失败: approvalId={}", approval.getId(), exception);
                }
            }
        });
    }
}

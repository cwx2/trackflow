package com.trackflow.automation.workitem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.workitem.entity.AutomationWorkItem;
import com.trackflow.automation.workitem.mapper.AutomationWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 可靠工作队列：幂等入队、SKIP LOCKED 领取、租约续期和失败退避。 */
@Service
@RequiredArgsConstructor
public class AutomationWorkItemService {
    private static final int DEFAULT_LEASE_SECONDS = 120;

    private final AutomationWorkItemMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public AutomationWorkItem enqueue(Long automationId, Long issueId, String correlationId,
                                      Map<String, Object> payload, int priority) {
        AutomationWorkItem item = new AutomationWorkItem();
        item.setAutomationId(automationId);
        item.setIssueId(issueId);
        item.setCorrelationId(correlationId);
        item.setState("queued");
        item.setPriority(priority);
        item.setAttempt(0);
        item.setMaxAttempts(3);
        item.setAvailableAt(LocalDateTime.now());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        try {
            item.setPayload(objectMapper.writeValueAsString(payload != null ? payload : Map.of()));
            mapper.insert(item);
            return item;
        } catch (DuplicateKeyException duplicate) {
            return mapper.selectOne(new LambdaQueryWrapper<AutomationWorkItem>()
                    .eq(AutomationWorkItem::getAutomationId, automationId)
                    .eq(AutomationWorkItem::getCorrelationId, correlationId));
        } catch (Exception exception) {
            throw new IllegalStateException("自动化工作入队失败", exception);
        }
    }

    @Transactional
    public AutomationWorkItem claim(String workerId) {
        releaseExpiredLeases();
        AutomationWorkItem item = mapper.selectOne(
                new LambdaQueryWrapper<AutomationWorkItem>()
                        .eq(AutomationWorkItem::getState, "queued")
                        .le(AutomationWorkItem::getAvailableAt, LocalDateTime.now())
                        .orderByDesc(AutomationWorkItem::getPriority)
                        .orderByAsc(AutomationWorkItem::getCreatedAt)
                        .last("FOR UPDATE SKIP LOCKED LIMIT 1"));
        if (item == null) return null;

        String token = UUID.randomUUID().toString();
        item.setState("leased");
        item.setLeaseOwner(workerId);
        item.setLeaseToken(token);
        item.setLeaseExpiresAt(LocalDateTime.now().plusSeconds(DEFAULT_LEASE_SECONDS));
        item.setAttempt(item.getAttempt() + 1);
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        return item;
    }

    public boolean renew(Long itemId, String token) {
        return mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getLeaseExpiresAt,
                        LocalDateTime.now().plusSeconds(DEFAULT_LEASE_SECONDS))
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, itemId)
                .eq(AutomationWorkItem::getLeaseToken, token)
                .in(AutomationWorkItem::getState, "leased", "running")) == 1;
    }

    public void markRunning(Long itemId, String token, Long executionId) {
        guardedUpdate(itemId, token, "running", update -> update
                .set(AutomationWorkItem::getExecutionId, executionId));
    }

    public void markCompleted(Long itemId) {
        mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, "completed")
                .set(AutomationWorkItem::getLeaseOwner, null)
                .set(AutomationWorkItem::getLeaseToken, null)
                .set(AutomationWorkItem::getLeaseExpiresAt, null)
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, itemId)
                .in(AutomationWorkItem::getState, "leased", "running"));
    }

    public void defer(AutomationWorkItem item, int seconds) {
        mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, "queued")
                .set(AutomationWorkItem::getAttempt, Math.max(0, item.getAttempt() - 1))
                .set(AutomationWorkItem::getAvailableAt, LocalDateTime.now().plusSeconds(seconds))
                .set(AutomationWorkItem::getLeaseOwner, null)
                .set(AutomationWorkItem::getLeaseToken, null)
                .set(AutomationWorkItem::getLeaseExpiresAt, null)
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, item.getId())
                .eq(AutomationWorkItem::getLeaseToken, item.getLeaseToken())
                .eq(AutomationWorkItem::getState, "leased"));
    }

    public void markCancelled(Long itemId) {
        mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, "cancelled")
                .set(AutomationWorkItem::getLeaseOwner, null)
                .set(AutomationWorkItem::getLeaseToken, null)
                .set(AutomationWorkItem::getLeaseExpiresAt, null)
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, itemId)
                .in(AutomationWorkItem::getState, "leased", "running"));
    }

    public java.util.List<AutomationWorkItem> listRunning(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<AutomationWorkItem>()
                .eq(AutomationWorkItem::getState, "running")
                .isNotNull(AutomationWorkItem::getExecutionId)
                .orderByAsc(AutomationWorkItem::getUpdatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public java.util.List<AutomationWorkItem> list(String state, int limit) {
        LambdaQueryWrapper<AutomationWorkItem> query = new LambdaQueryWrapper<AutomationWorkItem>()
                .orderByDesc(AutomationWorkItem::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        if (state != null && !state.isBlank()) query.eq(AutomationWorkItem::getState, state);
        return mapper.selectList(query);
    }

    public void retry(Long id) {
        int updated = mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, "queued")
                .set(AutomationWorkItem::getAttempt, 0)
                .set(AutomationWorkItem::getLastError, null)
                .set(AutomationWorkItem::getAvailableAt, LocalDateTime.now())
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, id)
                .in(AutomationWorkItem::getState, "failed", "dead_letter"));
        if (updated != 1) throw new IllegalStateException("只有失败或死信工作可以重新入队");
    }

    public void cancelQueued(Long id) {
        int updated = mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, "cancelled")
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, id)
                .in(AutomationWorkItem::getState, "queued", "leased"));
        if (updated != 1) throw new IllegalStateException("当前工作已开始或结束，不能从队列取消");
    }

    public void markFailed(AutomationWorkItem item, String error) {
        boolean dead = item.getAttempt() >= item.getMaxAttempts();
        long backoffSeconds = Math.min(3600, 10L * (1L << Math.min(8, item.getAttempt() - 1)));
        mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, dead ? "dead_letter" : "queued")
                .set(AutomationWorkItem::getLastError, error)
                .set(AutomationWorkItem::getAvailableAt, LocalDateTime.now().plusSeconds(backoffSeconds))
                .set(AutomationWorkItem::getLeaseOwner, null)
                .set(AutomationWorkItem::getLeaseToken, null)
                .set(AutomationWorkItem::getLeaseExpiresAt, null)
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, item.getId())
                .in(AutomationWorkItem::getState, "leased", "running"));
    }

    private void releaseExpiredLeases() {
        mapper.update(null, new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, "queued")
                .set(AutomationWorkItem::getLeaseOwner, null)
                .set(AutomationWorkItem::getLeaseToken, null)
                .set(AutomationWorkItem::getLeaseExpiresAt, null)
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getState, "leased")
                .le(AutomationWorkItem::getLeaseExpiresAt, LocalDateTime.now()));
    }

    private void guardedUpdate(Long itemId, String token, String state,
                               java.util.function.UnaryOperator<LambdaUpdateWrapper<AutomationWorkItem>> customizer) {
        LambdaUpdateWrapper<AutomationWorkItem> update = new LambdaUpdateWrapper<AutomationWorkItem>()
                .set(AutomationWorkItem::getState, state)
                .set(AutomationWorkItem::getUpdatedAt, LocalDateTime.now())
                .eq(AutomationWorkItem::getId, itemId)
                .eq(AutomationWorkItem::getLeaseToken, token)
                .eq(AutomationWorkItem::getState, "leased");
        if (mapper.update(null, customizer.apply(update)) != 1) {
            throw new IllegalStateException("工作租约已失效，拒绝重复执行");
        }
    }
}

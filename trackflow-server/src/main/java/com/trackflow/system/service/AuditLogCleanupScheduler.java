package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.system.entity.SysAuditLog;
import com.trackflow.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志定时清理任务
 * <p>
 * 每天凌晨 3:30 执行，删除超过保留期限的审计日志记录。
 * 保留天数从 system_setting 表读取（key: audit_log.retention_days），默认 180 天。
 * 设置为 0 表示永久保留。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupScheduler {

    private final SysAuditLogMapper auditLogMapper;
    private final SystemSettingService systemSettingService;

    /**
     * 每天凌晨 3:30 执行清理
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpiredAuditLogs() {
        int retentionDays;
        try {
            retentionDays = Integer.parseInt(
                    systemSettingService.getSettingValue("audit_log.retention_days", "180"));
        } catch (NumberFormatException e) {
            log.warn("[AuditLogCleanup] 保留天数配置无效，使用默认值 180 天");
            retentionDays = 180;
        }

        if (retentionDays <= 0) {
            log.debug("[AuditLogCleanup] 保留策略为永久保留（retention_days={}），跳过清理", retentionDays);
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("[AuditLogCleanup] 开始清理 {} 天前（{}之前）的审计日志...", retentionDays, cutoff);

        try {
            // 先统计待清理数量
            long count = auditLogMapper.selectCount(
                    new LambdaQueryWrapper<SysAuditLog>()
                            .lt(SysAuditLog::getCreatedAt, cutoff));

            if (count == 0) {
                log.info("[AuditLogCleanup] 无过期审计日志需要清理");
                return;
            }

            // 分批删除，避免长事务锁表（每批 1000 条）
            int totalDeleted = 0;
            int batchSize = 1000;
            int deleted;
            do {
                deleted = auditLogMapper.deleteBeforeCutoff(cutoff, batchSize);
                totalDeleted += deleted;
            } while (deleted >= batchSize);

            log.info("[AuditLogCleanup] 清理完成，共删除 {} 条过期审计日志（保留期 {} 天）", totalDeleted, retentionDays);
        } catch (Exception e) {
            log.error("[AuditLogCleanup] 清理过程中发生异常", e);
        }
    }
}

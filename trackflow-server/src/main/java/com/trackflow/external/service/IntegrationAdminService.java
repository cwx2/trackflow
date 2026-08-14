package com.trackflow.external.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.external.common.ExternalAdapter;
import com.trackflow.external.common.ExternalEventLog;
import com.trackflow.external.common.ExternalEventPublisher;
import com.trackflow.external.dto.IntegrationLogQuery;
import com.trackflow.external.dto.UpdateIntegrationConfigDTO;
import com.trackflow.external.mapper.ExternalEventLogMapper;
import com.trackflow.external.vo.IntegrationAdapterVO;
import com.trackflow.external.vo.IntegrationConfigVO;
import com.trackflow.external.vo.IntegrationLogVO;
import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 第三方集成管理 Service。
 * 负责适配器状态查询、配置读写、日志查询和重试操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationAdminService {

    private final List<ExternalAdapter> adapters;
    private final ExternalEventLogMapper eventLogMapper;
    private final SystemSettingService settingService;
    private final ExternalEventPublisher eventPublisher;

    private static final String KEY_PREFIX = "external.";
    private static final String ENABLED_SUFFIX = ".enabled";

    /**
     * 列出所有已注册的适配器及其状态
     */
    public List<IntegrationAdapterVO> listAdapters() {
        List<IntegrationAdapterVO> result = new ArrayList<>();

        for (ExternalAdapter adapter : adapters) {
            IntegrationAdapterVO vo = new IntegrationAdapterVO();
            vo.setAdapterType(adapter.getAdapterType());
            vo.setDisplayName(adapter.getDisplayName());
            vo.setEnabled(isAdapterEnabled(adapter.getAdapterType()));
            vo.setSupportedEvents(
                    adapter.getSupportedEvents().stream()
                            .map(e -> e.getCode())
                            .collect(Collectors.toList())
            );
            vo.setMaxRetries(adapter.getMaxRetries());
            vo.setRetryIntervalSeconds(adapter.getRetryIntervalSeconds());

            // 查询最近活动
            LambdaQueryWrapper<ExternalEventLog> lastActivityQuery = new LambdaQueryWrapper<>();
            lastActivityQuery.eq(ExternalEventLog::getAdapterType, adapter.getAdapterType())
                    .orderByDesc(ExternalEventLog::getCreatedAt)
                    .last("LIMIT 1");
            ExternalEventLog lastLog = eventLogMapper.selectOne(lastActivityQuery);
            if (lastLog != null) {
                vo.setLastActivityAt(lastLog.getCreatedAt());
            }

            // 统计计数
            LambdaQueryWrapper<ExternalEventLog> successQuery = new LambdaQueryWrapper<>();
            successQuery.eq(ExternalEventLog::getAdapterType, adapter.getAdapterType())
                    .eq(ExternalEventLog::getStatus, "success");
            vo.setSuccessCount(eventLogMapper.selectCount(successQuery));

            LambdaQueryWrapper<ExternalEventLog> failedQuery = new LambdaQueryWrapper<>();
            failedQuery.eq(ExternalEventLog::getAdapterType, adapter.getAdapterType())
                    .eq(ExternalEventLog::getStatus, "failed");
            vo.setFailedCount(eventLogMapper.selectCount(failedQuery));

            result.add(vo);
        }

        return result;
    }

    /**
     * 启用或禁用指定适配器
     */
    public void toggleAdapter(String adapterType, boolean enabled) {
        assertAdapterExists(adapterType);
        String key = KEY_PREFIX + adapterType + ENABLED_SUFFIX;
        settingService.upsertSetting(key, String.valueOf(enabled),
                adapterType + " 适配器启用状态", "external");
        log.info("[IntegrationAdmin] 适配器 {} 状态变更为 enabled={}", adapterType, enabled);
    }

    /**
     * 获取适配器配置
     */
    public IntegrationConfigVO getConfig(String adapterType) {
        ExternalAdapter adapter = assertAdapterExists(adapterType);

        IntegrationConfigVO vo = new IntegrationConfigVO();
        vo.setAdapterType(adapterType);
        vo.setDisplayName(adapter.getDisplayName());
        vo.setEnabled(isAdapterEnabled(adapterType));

        // 获取 external.{adapterType}.* 的所有配置项
        String prefix = KEY_PREFIX + adapterType + ".";
        var allSettings = settingService.getSettingsByPrefix(prefix);
        Map<String, String> configMap = new LinkedHashMap<>();
        for (var entry : allSettings.entrySet()) {
            // 去掉前缀，保留短 key
            String shortKey = entry.getKey().substring(prefix.length());
            if (!shortKey.equals("enabled")) { // enabled 单独展示
                configMap.put(shortKey, entry.getValue());
            }
        }
        vo.setConfig(configMap);

        return vo;
    }

    /**
     * 更新适配器配置
     */
    public IntegrationConfigVO updateConfig(String adapterType, UpdateIntegrationConfigDTO dto) {
        assertAdapterExists(adapterType);

        String prefix = KEY_PREFIX + adapterType + ".";
        for (Map.Entry<String, String> entry : dto.getConfig().entrySet()) {
            String fullKey = prefix + entry.getKey();
            settingService.upsertSetting(fullKey, entry.getValue(),
                    adapterType + " 配置: " + entry.getKey(), "external");
        }

        log.info("[IntegrationAdmin] 适配器 {} 配置已更新: {}", adapterType, dto.getConfig().keySet());
        return getConfig(adapterType);
    }

    /**
     * 分页查询集成日志
     */
    public Page<IntegrationLogVO> queryLogs(IntegrationLogQuery query) {
        LambdaQueryWrapper<ExternalEventLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getAdapterType() != null && !query.getAdapterType().isBlank()) {
            wrapper.eq(ExternalEventLog::getAdapterType, query.getAdapterType());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(ExternalEventLog::getStatus, query.getStatus());
        }
        if (query.getStartTime() != null && !query.getStartTime().isBlank()) {
            LocalDateTime start = LocalDateTime.parse(query.getStartTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            wrapper.ge(ExternalEventLog::getCreatedAt, start);
        }
        if (query.getEndTime() != null && !query.getEndTime().isBlank()) {
            LocalDateTime end = LocalDateTime.parse(query.getEndTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            wrapper.le(ExternalEventLog::getCreatedAt, end);
        }

        wrapper.orderByDesc(ExternalEventLog::getCreatedAt);

        Page<ExternalEventLog> page = new Page<>(query.getPage(), query.getPageSize());
        Page<ExternalEventLog> result = eventLogMapper.selectPage(page, wrapper);

        Page<IntegrationLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toLogVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 手动重试失败的事件
     */
    public IntegrationLogVO retryEvent(Long logId) {
        ExternalEventLog logEntry = eventLogMapper.selectById(logId);
        if (logEntry == null) {
            throw BusinessException.notFound("日志记录不存在");
        }
        if (!"failed".equals(logEntry.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只有失败状态的事件可以重试");
        }

        // 重置状态为 pending，增加重试计数
        logEntry.setStatus("pending");
        logEntry.setNextRetryAt(null);
        logEntry.setUpdatedAt(LocalDateTime.now());
        eventLogMapper.updateById(logEntry);

        // 查找对应适配器并重试
        ExternalAdapter adapter = findAdapter(logEntry.getAdapterType());
        if (adapter == null) {
            logEntry.setStatus("failed");
            logEntry.setErrorMessage("适配器不存在: " + logEntry.getAdapterType());
            logEntry.setUpdatedAt(LocalDateTime.now());
            eventLogMapper.updateById(logEntry);
            return toLogVO(logEntry);
        }

        try {
            logEntry.setStatus("processing");
            eventLogMapper.updateById(logEntry);

            // 构造简化事件重新发送
            adapter.handle(new com.trackflow.external.common.IssueExternalEvent(
                    logEntry.getEventType(),
                    logEntry.getReferenceId(),
                    logEntry.getPayload()
            ));

            logEntry.setStatus("success");
            logEntry.setErrorMessage(null);
            logEntry.setRetryCount(logEntry.getRetryCount() + 1);
            logEntry.setUpdatedAt(LocalDateTime.now());
            eventLogMapper.updateById(logEntry);
        } catch (Exception e) {
            logEntry.setStatus("failed");
            logEntry.setErrorMessage(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 2000))
                    : "Unknown error");
            logEntry.setRetryCount(logEntry.getRetryCount() + 1);
            logEntry.setUpdatedAt(LocalDateTime.now());
            eventLogMapper.updateById(logEntry);
        }

        return toLogVO(logEntry);
    }

    // ========= Private Helpers =========

    /**
     * 判断适配器是否启用（从 system_setting 读取）
     */
    public boolean isAdapterEnabled(String adapterType) {
        String key = KEY_PREFIX + adapterType + ENABLED_SUFFIX;
        String value = settingService.getSettingValue(key, "false");
        return "true".equalsIgnoreCase(value);
    }

    private ExternalAdapter assertAdapterExists(String adapterType) {
        ExternalAdapter adapter = findAdapter(adapterType);
        if (adapter == null) {
            throw BusinessException.notFound("适配器", adapterType);
        }
        return adapter;
    }

    private ExternalAdapter findAdapter(String adapterType) {
        return adapters.stream()
                .filter(a -> a.getAdapterType().equals(adapterType))
                .findFirst()
                .orElse(null);
    }

    private IntegrationLogVO toLogVO(ExternalEventLog entity) {
        IntegrationLogVO vo = new IntegrationLogVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setAdapterType(entity.getAdapterType());
        vo.setEventType(entity.getEventType());
        vo.setDirection(entity.getDirection());
        vo.setReferenceId(entity.getReferenceId());
        vo.setPayload(entity.getPayload());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setRetryCount(entity.getRetryCount());
        vo.setNextRetryAt(entity.getNextRetryAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}

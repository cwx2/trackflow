package com.trackflow.system.service;

import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.dto.UpdateTimeTrackingSettingsDTO;
import com.trackflow.system.dto.UpdateTimeTrackingSettingsDTO.RecalculationStrategy;
import com.trackflow.system.entity.SystemSetting;
import com.trackflow.system.mapper.SystemSettingMapper;
import com.trackflow.system.vo.TimeTrackingRecalculationResultVO;
import com.trackflow.system.vo.TimeTrackingSettingsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统设置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingMapper settingMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SystemAuditService auditService;

    private static final String KEY_HOURS_PER_DAY = "time_tracking.hours_per_day";
    private static final String KEY_WORKING_DAYS = "time_tracking.working_days";

    /**
     * 获取时间追踪设置
     */
    public TimeTrackingSettingsVO getTimeTrackingSettings() {
        TimeTrackingSettingsVO vo = new TimeTrackingSettingsVO();

        SystemSetting hoursSetting = settingMapper.selectByKey(KEY_HOURS_PER_DAY);
        vo.setHoursPerDay(hoursSetting != null ? Integer.parseInt(hoursSetting.getValue()) : 8);

        SystemSetting daysSetting = settingMapper.selectByKey(KEY_WORKING_DAYS);
        if (daysSetting != null) {
            try {
                List<Integer> days = objectMapper.readValue(daysSetting.getValue(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
                vo.setWorkingDays(days);
            } catch (Exception e) {
                log.warn("解析 working_days 配置失败，使用默认值", e);
                vo.setWorkingDays(List.of(1, 2, 3, 4, 5));
            }
        } else {
            vo.setWorkingDays(List.of(1, 2, 3, 4, 5));
        }

        return vo;
    }

    /**
     * 更新时间追踪设置（带工时重新计算支持）
     */
    @Transactional
    public TimeTrackingRecalculationResultVO updateTimeTrackingSettings(UpdateTimeTrackingSettingsDTO dto) {
        // 校验 workingDays 中的值在 1-7 范围内
        for (Integer day : dto.getWorkingDays()) {
            if (day < 1 || day > 7) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "工作日值必须在 1-7 之间，1=周一，7=周日");
            }
        }

        // 获取当前 hoursPerDay
        TimeTrackingSettingsVO currentSettings = getTimeTrackingSettings();
        int oldHoursPerDay = currentSettings.getHoursPerDay();
        int newHoursPerDay = dto.getHoursPerDay();
        boolean hoursChanged = oldHoursPerDay != newHoursPerDay;

        // 如果 hoursPerDay 变更但未提供策略，拒绝请求
        if (hoursChanged && dto.getRecalculationStrategy() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "每日工作小时数变更时必须指定重新计算策略（PRESERVE_MINUTES 或 PRESERVE_DAYS）");
        }

        // 更新 hours_per_day
        upsertSetting(KEY_HOURS_PER_DAY, String.valueOf(dto.getHoursPerDay()),
                "每日工作小时数，用于工时单位换算（1d = Xh）", "time_tracking");

        // 更新 working_days
        try {
            String workingDaysJson = objectMapper.writeValueAsString(dto.getWorkingDays());
            upsertSetting(KEY_WORKING_DAYS, workingDaysJson,
                    "每周工作日（1=周一，7=周日），JSON 数组", "time_tracking");
        } catch (Exception e) {
            throw new RuntimeException("序列化 workingDays 失败", e);
        }

        // 构建结果
        TimeTrackingRecalculationResultVO result = new TimeTrackingRecalculationResultVO();
        result.setOldHoursPerDay(oldHoursPerDay);
        result.setNewHoursPerDay(newHoursPerDay);

        int affectedTimeEntries = 0;
        int affectedEstimations = 0;

        // 执行重新计算（仅当 hoursPerDay 变更且策略为 PRESERVE_DAYS）
        if (hoursChanged && dto.getRecalculationStrategy() == RecalculationStrategy.PRESERVE_DAYS) {
            affectedTimeEntries = recalculateTimeEntryDurations(oldHoursPerDay, newHoursPerDay);
            affectedEstimations = recalculateIssueEstimations(oldHoursPerDay, newHoursPerDay);
            // 重新同步 spent_hours（从更新后的 time_entry 汇总）
            refreshAllSpentHours();

            result.setRecalculated(true);
            result.setStrategy("PRESERVE_DAYS");
            log.info("工时重新计算完成(PRESERVE_DAYS): {} → {}h/d, 影响工时记录 {} 条, 预估 {} 条",
                    oldHoursPerDay, newHoursPerDay, affectedTimeEntries, affectedEstimations);
        } else if (hoursChanged) {
            result.setRecalculated(false);
            result.setStrategy("PRESERVE_MINUTES");
            log.info("时间追踪设置已更新(PRESERVE_MINUTES): {} → {}h/d, 数据不变仅更新换算",
                    oldHoursPerDay, newHoursPerDay);
        } else {
            result.setRecalculated(false);
            result.setStrategy(null);
            log.info("时间追踪设置已更新: hoursPerDay 未变, workingDays={}", dto.getWorkingDays());
        }

        result.setAffectedTimeEntries(affectedTimeEntries);
        result.setAffectedEstimations(affectedEstimations);
        result.setSettings(getTimeTrackingSettings());

        // 审计日志
        auditService.log("time_tracking_settings_update", "system_setting", null,
                Map.of(
                        "oldHoursPerDay", oldHoursPerDay,
                        "newHoursPerDay", newHoursPerDay,
                        "strategy", dto.getRecalculationStrategy() != null ? dto.getRecalculationStrategy().name() : "NONE",
                        "affectedTimeEntries", affectedTimeEntries,
                        "affectedEstimations", affectedEstimations
                ));

        return result;
    }

    /**
     * 重新计算 time_entry.duration（保留天数策略）。
     * 公式：new_duration = ROUND(old_duration * new_hoursPerDay / old_hoursPerDay)
     */
    private int recalculateTimeEntryDurations(int oldHoursPerDay, int newHoursPerDay) {
        String sql = """
                UPDATE time_entry
                SET duration = ROUND(duration::numeric * ? / ?),
                    updated_at = NOW()
                WHERE duration IS NOT NULL AND ongoing = false
                """;
        return jdbcTemplate.update(sql, newHoursPerDay, oldHoursPerDay);
    }

    /**
     * 重新计算 issue.estimated_hours（保留天数策略）。
     * estimated_hours 存储为小数小时，公式同理。
     */
    private int recalculateIssueEstimations(int oldHoursPerDay, int newHoursPerDay) {
        String sql = """
                UPDATE issue
                SET estimated_hours = ROUND(estimated_hours * ? / ?, 2),
                    updated_at = NOW()
                WHERE estimated_hours IS NOT NULL AND estimated_hours > 0
                """;
        return jdbcTemplate.update(sql, newHoursPerDay, oldHoursPerDay);
    }

    /**
     * 重新同步所有 issue 的 spent_hours（从 time_entry 汇总）
     */
    private void refreshAllSpentHours() {
        String sql = """
                UPDATE issue
                SET spent_hours = COALESCE(
                    (SELECT SUM(te.duration) / 60.0
                     FROM time_entry te
                     WHERE te.issue_id = issue.id AND te.ongoing = false),
                    0
                ),
                updated_at = NOW()
                WHERE id IN (SELECT DISTINCT issue_id FROM time_entry WHERE ongoing = false)
                """;
        jdbcTemplate.update(sql);
    }

    /**
     * 获取单个配置值
     */
    public String getSettingValue(String key, String defaultValue) {
        SystemSetting setting = settingMapper.selectByKey(key);
        return setting != null ? setting.getValue() : defaultValue;
    }

    public void upsertSetting(String key, String value, String description, String category) {
        SystemSetting existing = settingMapper.selectByKey(key);
        if (existing != null) {
            existing.setValue(value);
            existing.setUpdatedAt(LocalDateTime.now());
            settingMapper.updateById(existing);
        } else {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setValue(value);
            setting.setDescription(description);
            setting.setCategory(category);
            setting.setCreatedAt(LocalDateTime.now());
            setting.setUpdatedAt(LocalDateTime.now());
            settingMapper.insert(setting);
        }
    }
}

package com.trackflow.system.service;

import com.trackflow.system.dto.UpdateTimeTrackingSettingsDTO;
import com.trackflow.system.entity.SystemSetting;
import com.trackflow.system.mapper.SystemSettingMapper;
import com.trackflow.system.vo.TimeTrackingSettingsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统设置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingMapper settingMapper;
    private final ObjectMapper objectMapper;

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
     * 更新时间追踪设置
     */
    @Transactional
    public TimeTrackingSettingsVO updateTimeTrackingSettings(UpdateTimeTrackingSettingsDTO dto) {
        // 校验 workingDays 中的值在 1-7 范围内
        for (Integer day : dto.getWorkingDays()) {
            if (day < 1 || day > 7) {
                throw new IllegalArgumentException("工作日值必须在 1-7 之间，1=周一，7=周日");
            }
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

        log.info("时间追踪设置已更新: hoursPerDay={}, workingDays={}", dto.getHoursPerDay(), dto.getWorkingDays());
        return getTimeTrackingSettings();
    }

    /**
     * 获取单个配置值
     */
    public String getSettingValue(String key, String defaultValue) {
        SystemSetting setting = settingMapper.selectByKey(key);
        return setting != null ? setting.getValue() : defaultValue;
    }

    private void upsertSetting(String key, String value, String description, String category) {
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

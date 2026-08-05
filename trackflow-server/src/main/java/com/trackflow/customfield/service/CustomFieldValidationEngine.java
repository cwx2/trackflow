package com.trackflow.customfield.service;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.handler.CustomFieldHandlerRegistry;
import com.trackflow.customfield.handler.CustomFieldTypeHandler;
import com.trackflow.customfield.handler.ValidationContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 自定义字段值验证引擎 — 通过 {@link CustomFieldHandlerRegistry} 委托给各类型 Handler 执行校验。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFieldValidationEngine {

    private final CustomFieldHandlerRegistry handlerRegistry;

    /**
     * period（时间周期）格式的正则表达式
     * 支持格式: 1w2d3h30m, 2h30m, 1d, 45m, 2h, 1w 等
     * 也支持纯分钟数（整数）
     */
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "^(?:(\\d+)w)?\\s*(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?$",
            Pattern.CASE_INSENSITIVE
    );

    /** 无项目上下文时使用的兼容入口。 */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value) {
        return validate(field, value, null, null);
    }

    /**
     * 验证单个字段值（带项目上下文）。
     *
     * @param field     字段定义
     * @param value     待验证的值
     * @param projectId 工单所属项目 ID（用于 user 类型的成员校验）
     */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, Long projectId) {
        return validate(field, value, projectId, null);
    }

    /**
     * 验证单个字段值（带项目上下文和项目级必填性覆盖）。
     *
     * @param field              字段定义
     * @param value              待验证的值
     * @param projectId          工单所属项目 ID
     * @param effectiveRequired  项目级必填性覆盖（null = 使用字段定义的 isRequired）
     */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, Long projectId, Boolean effectiveRequired) {
        List<FieldValidationError> errors = new ArrayList<>();

        // 必填检查：项目级覆盖 > 全局定义
        boolean isRequired = effectiveRequired != null ? effectiveRequired : Boolean.TRUE.equals(field.getIsRequired());
        if (isRequired && (value == null || value.isBlank())) {
            errors.add(new FieldValidationError(field.getName(), "此字段为必填项"));
            return errors;
        }

        // 非必填且无值时跳过
        if (value == null || value.isBlank()) {
            return errors;
        }

        // 通过 HandlerRegistry 委托给对应类型的 Handler 执行校验
        var handler = handlerRegistry.getHandler(field.getFieldFormat());
        if (handler.isPresent()) {
            return handler.get().validate(field, value, ValidationContext.of(projectId));
        } else {
            log.error("Unsupported field_format '{}' for field '{}' (id={})",
                    field.getFieldFormat(), field.getName(), field.getId());
            errors.add(new FieldValidationError(field.getName(), "不支持的字段类型: " + field.getFieldFormat()));
            return errors;
        }
    }

    /**
     * 工作时间常量（可由系统配置覆盖，默认 Scrum 标准）
     * 1 周 = 5 工作天, 1 天 = 8 工作小时
     */
    public static final int WORK_HOURS_PER_DAY = 8;
    public static final int WORK_DAYS_PER_WEEK = 5;
    public static final int MINUTES_PER_WORK_HOUR = 60;
    public static final int MINUTES_PER_WORK_DAY = WORK_HOURS_PER_DAY * MINUTES_PER_WORK_HOUR;  // 480
    public static final int MINUTES_PER_WORK_WEEK = WORK_DAYS_PER_WEEK * MINUTES_PER_WORK_DAY;  // 2400

    /**
     * 将周期表达式解析为分钟数（基于工作时间）
     *
     * 换算规则：1w = 5d = 40h = 2400m
     *
     * @param periodStr 周期表达式，如 "1w2d3h30m", "2h30m", "1d", "45m"
     * @return 总分钟数，解析失败返回 null
     */
    public static Long parsePeriodToMinutes(String periodStr) {
        if (periodStr == null || periodStr.isBlank()) {
            return null;
        }

        String trimmed = periodStr.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }

        // 先尝试纯数字
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
        }

        // 解析周期表达式
        var matcher = PERIOD_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }

        long totalMinutes = 0;

        String weeks = matcher.group(1);
        String days = matcher.group(2);
        String hours = matcher.group(3);
        String mins = matcher.group(4);

        // 至少要有一个部分
        if (weeks == null && days == null && hours == null && mins == null) {
            return null;
        }

        if (weeks != null) {
            totalMinutes += Long.parseLong(weeks) * MINUTES_PER_WORK_WEEK;
        }
        if (days != null) {
            totalMinutes += Long.parseLong(days) * MINUTES_PER_WORK_DAY;
        }
        if (hours != null) {
            totalMinutes += Long.parseLong(hours) * MINUTES_PER_WORK_HOUR;
        }
        if (mins != null) {
            totalMinutes += Long.parseLong(mins);
        }

        return totalMinutes;
    }

    /**
     * 将分钟数格式化为人可读的周期字符串（基于工作时间）
     *
     * 换算规则：1w = 5d = 40h = 2400m
     *
     * @param minutes 分钟数
     * @return 格式化字符串，如 "1w 2d 3h 30m"
     */
    public static String formatMinutesToPeriod(long minutes) {
        if (minutes <= 0) {
            return "0m";
        }

        long remaining = minutes;
        StringBuilder sb = new StringBuilder();

        long weeks = remaining / MINUTES_PER_WORK_WEEK;
        if (weeks > 0) {
            sb.append(weeks).append("w ");
            remaining %= MINUTES_PER_WORK_WEEK;
        }

        long days = remaining / MINUTES_PER_WORK_DAY;
        if (days > 0) {
            sb.append(days).append("d ");
            remaining %= MINUTES_PER_WORK_DAY;
        }

        long hours = remaining / MINUTES_PER_WORK_HOUR;
        if (hours > 0) {
            sb.append(hours).append("h ");
            remaining %= MINUTES_PER_WORK_HOUR;
        }

        if (remaining > 0) {
            sb.append(remaining).append("m");
        }

        return sb.toString().trim();
    }

    @Data
    public static class FieldValidationError {
        private final String field;
        private final String message;
    }
}

package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 时间周期字段处理器
 * <p>
 * 支持两种输入格式：
 * 1. 周期表达式：1w2d3h30m, 2h30m, 1d, 45m 等
 * 2. 纯分钟数：整数值（如 "150" 表示 2h30m）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class PeriodFieldHandler implements CustomFieldTypeHandler {

    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "^(?:(\\d+)w)?\\s*(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String fieldFormat() {
        return "period";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();

        // 先尝试作为纯整数（分钟数）解析
        try {
            long minutes = Long.parseLong(value);
            if (minutes < 0) {
                errors.add(new FieldValidationError(field.getName(), "时间周期不能为负数"));
            }
            return errors;
        } catch (NumberFormatException ignored) {
            // 不是纯数字，继续尝试周期表达式格式
        }

        // 尝试解析周期表达式
        Long minutes = CustomFieldValidationEngine.parsePeriodToMinutes(value);
        if (minutes == null) {
            errors.add(new FieldValidationError(field.getName(),
                    "时间周期格式不正确，期望如 1w2d3h30m、2h30m、45m 或纯分钟数"));
        } else if (minutes < 0) {
            errors.add(new FieldValidationError(field.getName(), "时间周期不能为负数"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        try {
            long minutes = Long.parseLong(rawValue);
            return CustomFieldValidationEngine.formatMinutesToPeriod(minutes);
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    @Override
    public String typeLabel() {
        return "时间周期";
    }

    @Override
    public boolean isSortable() {
        return false;
    }
}

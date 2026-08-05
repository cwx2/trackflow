package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期时间字段处理器（yyyy-MM-ddTHH:mm:ss）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class DateTimeFieldHandler implements CustomFieldTypeHandler {

    @Override
    public String fieldFormat() {
        return "datetime";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        try {
            LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(new FieldValidationError(field.getName(),
                    "值格式不正确，期望日期时间 (yyyy-MM-ddTHH:mm:ss)"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        return rawValue;
    }

    @Override
    public String typeLabel() {
        return "日期时间";
    }
}

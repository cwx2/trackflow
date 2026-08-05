package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期字段处理器（yyyy-MM-dd）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class DateFieldHandler implements CustomFieldTypeHandler {

    @Override
    public String fieldFormat() {
        return "date";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望日期 (yyyy-MM-dd)"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        return rawValue;
    }

    @Override
    public String typeLabel() {
        return "日期";
    }
}

package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 整数字段处理器
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class IntFieldHandler implements CustomFieldTypeHandler {

    @Override
    public String fieldFormat() {
        return "int";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        try {
            Long.parseLong(value);
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望整数"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        return rawValue;
    }

    @Override
    public String toSortExpression(Long cfId) {
        return "(SELECT cfv.value::BIGINT FROM custom_field_value cfv " +
                "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
    }

    @Override
    public String typeLabel() {
        return "整数";
    }
}

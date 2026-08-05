package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 布尔值字段处理器
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class BoolFieldHandler implements CustomFieldTypeHandler {

    @Override
    public String fieldFormat() {
        return "bool";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        if (!"true".equals(value) && !"false".equals(value)) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望 true 或 false"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        return "true".equals(rawValue) ? "是" : "否";
    }

    @Override
    public String typeLabel() {
        return "布尔值";
    }

    @Override
    public boolean isSortable() {
        return false;
    }
}

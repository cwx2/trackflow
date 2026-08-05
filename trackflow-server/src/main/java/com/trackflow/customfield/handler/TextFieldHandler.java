package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 多行文本字段处理器（富文本/Markdown）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class TextFieldHandler implements CustomFieldTypeHandler {

    @Override
    public String fieldFormat() {
        return "text";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        if (field.getMaxLength() != null && field.getMaxLength() > 0 && value.length() > field.getMaxLength()) {
            errors.add(new FieldValidationError(field.getName(),
                    "内容长度不能超过 " + field.getMaxLength() + " 个字符"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        return rawValue;
    }

    @Override
    public String typeLabel() {
        return "文本（多行）";
    }

    @Override
    public boolean isSortable() {
        return false;
    }
}

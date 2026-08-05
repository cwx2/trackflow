package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 单行文本字段处理器
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
public class StringFieldHandler implements CustomFieldTypeHandler {

    @Override
    public String fieldFormat() {
        return "string";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();

        // 长度校验
        if (field.getMinLength() != null && field.getMinLength() > 0 && value.length() < field.getMinLength()) {
            errors.add(new FieldValidationError(field.getName(),
                    "长度必须在 " + field.getMinLength() + "-" + field.getMaxLength() + " 之间"));
            return errors;
        }
        if (field.getMaxLength() != null && field.getMaxLength() > 0 && value.length() > field.getMaxLength()) {
            errors.add(new FieldValidationError(field.getName(),
                    "长度必须在 " + field.getMinLength() + "-" + field.getMaxLength() + " 之间"));
            return errors;
        }

        // 正则校验
        if (field.getRegexp() != null && !field.getRegexp().isBlank()) {
            try {
                if (!Pattern.matches(field.getRegexp(), value)) {
                    errors.add(new FieldValidationError(field.getName(),
                            "值不匹配规则 " + field.getRegexp()));
                }
            } catch (PatternSyntaxException e) {
                // 正则本身有问题，跳过校验
            }
        }

        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        return rawValue;
    }

    @Override
    public String typeLabel() {
        return "文本（单行）";
    }
}

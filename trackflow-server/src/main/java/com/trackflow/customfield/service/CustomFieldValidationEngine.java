package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 自定义字段值验证引擎
 * 使用策略模式按 field_format 分发验证逻辑
 */
@Component
@RequiredArgsConstructor
public class CustomFieldValidationEngine {

    private final CustomFieldOptionMapper optionMapper;
    private final SysUserMapper userMapper;

    public static final Set<String> SUPPORTED_FORMATS = Set.of(
            "string", "text", "int", "float", "date", "datetime", "bool", "list", "user"
    );

    /**
     * 验证单个字段值
     */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value) {
        List<FieldValidationError> errors = new ArrayList<>();

        // 必填检查
        if (Boolean.TRUE.equals(field.getIsRequired()) && (value == null || value.isBlank())) {
            errors.add(new FieldValidationError(field.getName(), "此字段为必填项"));
            return errors;
        }

        // 非必填且无值时跳过
        if (value == null || value.isBlank()) {
            return errors;
        }

        // 按类型分发验证
        switch (field.getFieldFormat()) {
            case "string" -> validateString(field, value, errors);
            case "text" -> validateText(field, value, errors);
            case "int" -> validateInt(field, value, errors);
            case "float" -> validateFloat(field, value, errors);
            case "date" -> validateDate(field, value, errors);
            case "datetime" -> validateDatetime(field, value, errors);
            case "bool" -> validateBool(field, value, errors);
            case "list" -> validateList(field, value, errors);
            case "user" -> validateUser(field, value, errors);
        }

        return errors;
    }

    private void validateString(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        // 长度校验
        if (field.getMinLength() != null && field.getMinLength() > 0 && value.length() < field.getMinLength()) {
            errors.add(new FieldValidationError(field.getName(),
                    "长度必须在 " + field.getMinLength() + "-" + field.getMaxLength() + " 之间"));
            return;
        }
        if (field.getMaxLength() != null && field.getMaxLength() > 0 && value.length() > field.getMaxLength()) {
            errors.add(new FieldValidationError(field.getName(),
                    "长度必须在 " + field.getMinLength() + "-" + field.getMaxLength() + " 之间"));
            return;
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
    }

    /**
     * text 类型（多行富文本/Markdown）
     * 仅做最大长度校验（可选），不做正则校验
     */
    private void validateText(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        if (field.getMaxLength() != null && field.getMaxLength() > 0 && value.length() > field.getMaxLength()) {
            errors.add(new FieldValidationError(field.getName(),
                    "内容长度不能超过 " + field.getMaxLength() + " 个字符"));
        }
    }

    private void validateInt(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        try {
            Long.parseLong(value);
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望整数"));
        }
    }

    private void validateFloat(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望数字"));
        }
    }

    private void validateDate(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望日期 (yyyy-MM-dd)"));
        }
    }

    /**
     * datetime 类型（日期+时间）
     * 接受 ISO-8601 格式: yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-ddTHH:mm
     */
    private void validateDatetime(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        try {
            LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(new FieldValidationError(field.getName(),
                    "值格式不正确，期望日期时间 (yyyy-MM-ddTHH:mm:ss)"));
        }
    }

    private void validateBool(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        if (!"true".equals(value) && !"false".equals(value)) {
            errors.add(new FieldValidationError(field.getName(), "值格式不正确，期望 true 或 false"));
        }
    }

    /**
     * list 类型验证
     * 单值模式：value 为单个选项 ID
     * 多值模式（isMulti=true）：value 为逗号分隔的选项 ID 列表
     */
    private void validateList(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        boolean isMulti = Boolean.TRUE.equals(field.getIsMulti());

        if (isMulti) {
            // 多值模式：逗号分隔的 ID 列表
            String[] ids = value.split(",");
            for (String idStr : ids) {
                String trimmed = idStr.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    Long optionId = Long.parseLong(trimmed);
                    boolean exists = optionMapper.exists(new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, field.getId())
                            .eq(CustomFieldOption::getId, optionId));
                    if (!exists) {
                        errors.add(new FieldValidationError(field.getName(), "无效的选项值: " + trimmed));
                        return;
                    }
                } catch (NumberFormatException e) {
                    errors.add(new FieldValidationError(field.getName(), "无效的选项值: " + trimmed));
                    return;
                }
            }
        } else {
            // 单值模式
            try {
                Long optionId = Long.parseLong(value);
                boolean exists = optionMapper.exists(new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, field.getId())
                        .eq(CustomFieldOption::getId, optionId));
                if (!exists) {
                    errors.add(new FieldValidationError(field.getName(), "无效的选项值"));
                }
            } catch (NumberFormatException e) {
                errors.add(new FieldValidationError(field.getName(), "无效的选项值"));
            }
        }
    }

    private void validateUser(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        try {
            Long userId = Long.parseLong(value);
            if (userMapper.selectById(userId) == null) {
                errors.add(new FieldValidationError(field.getName(), "无效的用户"));
            }
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "无效的用户"));
        }
    }

    @Data
    public static class FieldValidationError {
        private final String field;
        private final String message;
    }
}

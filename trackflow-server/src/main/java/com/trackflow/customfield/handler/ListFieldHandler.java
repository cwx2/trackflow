package com.trackflow.customfield.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 枚举列表字段处理器（单选/多选枚举）
 * <p>
 * 同时覆盖 list、state、ownedField、version 四种 fieldFormat，
 * 它们在校验/展示/排序逻辑上完全一致（都基于 custom_field_option 表）。
 * 差异仅在 typeLabel 和是否有额外属性（version 有 releaseDate 等），
 * 因此 state/ownedField/version 各自有独立 Handler 继承本类。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class ListFieldHandler implements CustomFieldTypeHandler {

    private final CustomFieldOptionMapper optionMapper;

    @Override
    public String fieldFormat() {
        return "list";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        boolean isMulti = Boolean.TRUE.equals(field.getIsMulti());

        if (isMulti) {
            String[] ids = value.split(",");
            for (String idStr : ids) {
                String trimmed = idStr.trim();
                if (trimmed.isEmpty()) continue;
                validateSingleOption(field, trimmed, errors);
                if (!errors.isEmpty()) return errors;
            }
        } else {
            validateSingleOption(field, value, errors);
        }
        return errors;
    }

    private void validateSingleOption(CustomFieldDefinition field, String valueStr, List<FieldValidationError> errors) {
        try {
            Long optionId = Long.parseLong(valueStr);
            boolean exists = optionMapper.exists(new LambdaQueryWrapper<CustomFieldOption>()
                    .eq(CustomFieldOption::getCustomFieldId, field.getId())
                    .eq(CustomFieldOption::getId, optionId)
                    .eq(CustomFieldOption::getIsArchived, false));
            if (!exists) {
                String errorMsg = resolveOptionError(field, optionId, valueStr);
                errors.add(new FieldValidationError(field.getName(), errorMsg));
            }
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "无效的选项值: " + valueStr));
        }
    }

    private String resolveOptionError(CustomFieldDefinition field, Long optionId, String displayValue) {
        boolean existsAsArchived = optionMapper.exists(new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, field.getId())
                .eq(CustomFieldOption::getId, optionId)
                .eq(CustomFieldOption::getIsArchived, true));
        if (existsAsArchived) {
            return "选项已归档，不可选择: " + displayValue;
        }
        return "无效的选项值: " + displayValue;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        try {
            Long optionId = Long.parseLong(rawValue);
            Map<Long, String> optionTextMap = displayContext.optionTextMap();
            return optionTextMap.getOrDefault(optionId, rawValue);
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    @Override
    public String toSortExpression(Long cfId) {
        // 按选项的 position 排序（管理员定义的选项顺序）
        return "(SELECT cfo.position FROM custom_field_value cfv " +
                "JOIN custom_field_option cfo ON cfo.id = cfv.value::BIGINT " +
                "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
    }

    @Override
    public String typeLabel() {
        return "枚举列表";
    }
}

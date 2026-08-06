package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import com.trackflow.system.mapper.UserGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户组字段处理器 — 校验用户组存在性，展示用户组名称。
 * <p>
 * 字段值存储用户组 ID（Long），展示值为用户组名称。
 * 支持单值和多值配置。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class GroupFieldHandler implements CustomFieldTypeHandler {

    private final UserGroupMapper userGroupMapper;

    @Override
    public String fieldFormat() {
        return "group";
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
                validateSingleGroup(field, trimmed, errors);
                if (!errors.isEmpty()) return errors;
            }
        } else {
            validateSingleGroup(field, value, errors);
        }
        return errors;
    }

    private void validateSingleGroup(CustomFieldDefinition field, String valueStr, List<FieldValidationError> errors) {
        try {
            Long groupId = Long.parseLong(valueStr);
            if (userGroupMapper.selectById(groupId) == null) {
                errors.add(new FieldValidationError(field.getName(), "无效的用户组: " + valueStr));
            }
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "无效的用户组: " + valueStr));
        }
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        try {
            Long groupId = Long.parseLong(rawValue);
            Map<Long, String> groupNameMap = displayContext.groupNameMap();
            if (groupNameMap != null) {
                return groupNameMap.getOrDefault(groupId, rawValue);
            }
            return rawValue;
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    @Override
    public String toSortExpression(Long cfId) {
        // 按用户组名称排序
        return "(SELECT ug.name FROM custom_field_value cfv " +
                "JOIN user_group ug ON ug.id = cfv.value::BIGINT " +
                "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
    }

    @Override
    public String typeLabel() {
        return "用户组";
    }
}

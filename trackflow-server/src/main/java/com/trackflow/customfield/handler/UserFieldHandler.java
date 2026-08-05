package com.trackflow.customfield.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户字段处理器 — 校验用户存在性和项目成员身份
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class UserFieldHandler implements CustomFieldTypeHandler {

    private final SysUserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public String fieldFormat() {
        return "user";
    }

    @Override
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context) {
        List<FieldValidationError> errors = new ArrayList<>();
        try {
            Long userId = Long.parseLong(value);
            if (userMapper.selectById(userId) == null) {
                errors.add(new FieldValidationError(field.getName(), "无效的用户"));
                return errors;
            }
            // 校验用户是否为当前项目成员（参考 OpenProject possible_users 限定范围）
            if (context.projectId() != null) {
                boolean isMember = projectMemberMapper.exists(
                        new LambdaQueryWrapper<ProjectMember>()
                                .eq(ProjectMember::getProjectId, context.projectId())
                                .eq(ProjectMember::getUserId, userId)
                );
                if (!isMember) {
                    errors.add(new FieldValidationError(field.getName(), "该用户不是本项目成员"));
                }
            }
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "无效的用户"));
        }
        return errors;
    }

    @Override
    public String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext) {
        try {
            Long userId = Long.parseLong(rawValue);
            Map<Long, String> userNameMap = displayContext.userNameMap();
            return userNameMap.getOrDefault(userId, rawValue);
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    @Override
    public String toSortExpression(Long cfId) {
        // 按用户显示名排序
        return "(SELECT u.display_name FROM custom_field_value cfv " +
                "JOIN sys_user u ON u.id = cfv.value::BIGINT " +
                "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
    }

    @Override
    public String typeLabel() {
        return "用户";
    }
}

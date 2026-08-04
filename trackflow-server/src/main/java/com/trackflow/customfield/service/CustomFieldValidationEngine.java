package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 自定义字段值验证引擎
 * 使用策略模式按 field_format 分发验证逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFieldValidationEngine {

    private final CustomFieldOptionMapper optionMapper;
    private final SysUserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;

    public static final Set<String> SUPPORTED_FORMATS = Set.of(
            "string", "text", "int", "float", "date", "datetime", "bool", "list", "user", "period", "state"
    );

    /** 无项目上下文时使用的兼容入口。 */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value) {
        return validate(field, value, null, null);
    }

    /**
     * period（时间周期）格式的正则表达式
     * 支持格式: 1w2d3h30m, 2h30m, 1d, 45m, 2h, 1w 等
     * 也支持纯分钟数（整数）
     */
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "^(?:(\\d+)w)?\\s*(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证单个字段值（带项目上下文）。
     * user 类型字段会校验用户是否为指定项目的成员。
     *
     * @param field     字段定义
     * @param value     待验证的值
     * @param projectId 工单所属项目 ID（用于 user 类型的成员校验）
     */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, Long projectId) {
        return validate(field, value, projectId, null);
    }

    /**
     * 验证单个字段值（带项目上下文和项目级必填性覆盖）。
     *
     * @param field              字段定义
     * @param value              待验证的值
     * @param projectId          工单所属项目 ID
     * @param effectiveRequired  项目级必填性覆盖（null = 使用字段定义的 isRequired）
     */
    public List<FieldValidationError> validate(CustomFieldDefinition field, String value, Long projectId, Boolean effectiveRequired) {
        List<FieldValidationError> errors = new ArrayList<>();

        // 必填检查：项目级覆盖 > 全局定义
        boolean isRequired = effectiveRequired != null ? effectiveRequired : Boolean.TRUE.equals(field.getIsRequired());
        if (isRequired && (value == null || value.isBlank())) {
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
            case "list", "state" -> validateList(field, value, errors);
            case "user" -> validateUser(field, value, projectId, errors);
            case "period" -> validatePeriod(field, value, errors);
            default -> {
                log.error("Unsupported field_format '{}' for field '{}' (id={})",
                        field.getFieldFormat(), field.getName(), field.getId());
                errors.add(new FieldValidationError(field.getName(), "不支持的字段类型: " + field.getFieldFormat()));
            }
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
                            .eq(CustomFieldOption::getId, optionId)
                            .eq(CustomFieldOption::getIsArchived, false));
                    if (!exists) {
                        String errorMsg = resolveOptionError(field, optionId, trimmed);
                        errors.add(new FieldValidationError(field.getName(), errorMsg));
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
                        .eq(CustomFieldOption::getId, optionId)
                        .eq(CustomFieldOption::getIsArchived, false));
                if (!exists) {
                    String errorMsg = resolveOptionError(field, optionId, value);
                    errors.add(new FieldValidationError(field.getName(), errorMsg));
                }
            } catch (NumberFormatException e) {
                errors.add(new FieldValidationError(field.getName(), "无效的选项值"));
            }
        }
    }

    /**
     * 区分选项验证失败原因：不存在 vs 已归档
     * 仅在验证失败时执行二次查询，不影响正常路径性能
     */
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

    private void validateUser(CustomFieldDefinition field, String value, Long projectId, List<FieldValidationError> errors) {
        try {
            Long userId = Long.parseLong(value);
            if (userMapper.selectById(userId) == null) {
                errors.add(new FieldValidationError(field.getName(), "无效的用户"));
                return;
            }
            // 校验用户是否为当前项目成员（参考 OpenProject possible_users 限定范围）
            if (projectId != null) {
                boolean isMember = projectMemberMapper.exists(
                        new LambdaQueryWrapper<ProjectMember>()
                                .eq(ProjectMember::getProjectId, projectId)
                                .eq(ProjectMember::getUserId, userId)
                );
                if (!isMember) {
                    errors.add(new FieldValidationError(field.getName(), "该用户不是本项目成员"));
                }
            }
        } catch (NumberFormatException e) {
            errors.add(new FieldValidationError(field.getName(), "无效的用户"));
        }
    }

    /**
     * period（时间周期）类型验证
     * 支持两种输入格式：
     * 1. 周期表达式：1w2d3h30m, 2h30m, 1d, 45m 等
     * 2. 纯分钟数：整数值（如 "150" 表示 2h30m）
     *
     * 验证通过后，值会被标准化存储为分钟数字符串
     */
    private void validatePeriod(CustomFieldDefinition field, String value, List<FieldValidationError> errors) {
        // 先尝试作为纯整数（分钟数）解析
        try {
            long minutes = Long.parseLong(value);
            if (minutes < 0) {
                errors.add(new FieldValidationError(field.getName(), "时间周期不能为负数"));
            }
            return; // 纯数字格式有效
        } catch (NumberFormatException ignored) {
            // 不是纯数字，继续尝试周期表达式格式
        }

        // 尝试解析周期表达式
        Long minutes = parsePeriodToMinutes(value);
        if (minutes == null) {
            errors.add(new FieldValidationError(field.getName(),
                    "时间周期格式不正确，期望如 1w2d3h30m、2h30m、45m 或纯分钟数"));
        } else if (minutes < 0) {
            errors.add(new FieldValidationError(field.getName(), "时间周期不能为负数"));
        }
    }

    /**
     * 将周期表达式解析为分钟数
     *
     * @param periodStr 周期表达式，如 "1w2d3h30m", "2h30m", "1d", "45m"
     * @return 总分钟数，解析失败返回 null
     */
    public static Long parsePeriodToMinutes(String periodStr) {
        if (periodStr == null || periodStr.isBlank()) {
            return null;
        }

        String trimmed = periodStr.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }

        // 先尝试纯数字
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
        }

        // 解析周期表达式
        var matcher = PERIOD_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }

        long totalMinutes = 0;

        String weeks = matcher.group(1);
        String days = matcher.group(2);
        String hours = matcher.group(3);
        String mins = matcher.group(4);

        // 至少要有一个部分
        if (weeks == null && days == null && hours == null && mins == null) {
            return null;
        }

        if (weeks != null) {
            totalMinutes += Long.parseLong(weeks) * 7 * 24 * 60; // 1 week = 7 * 24 * 60 minutes
        }
        if (days != null) {
            totalMinutes += Long.parseLong(days) * 24 * 60; // 1 day = 24 * 60 minutes
        }
        if (hours != null) {
            totalMinutes += Long.parseLong(hours) * 60;
        }
        if (mins != null) {
            totalMinutes += Long.parseLong(mins);
        }

        return totalMinutes;
    }

    /**
     * 将分钟数格式化为人可读的周期字符串
     *
     * @param minutes 分钟数
     * @return 格式化字符串，如 "1周 2天 3小时 30分钟"
     */
    public static String formatMinutesToPeriod(long minutes) {
        if (minutes <= 0) {
            return "0分钟";
        }

        long remaining = minutes;
        StringBuilder sb = new StringBuilder();

        // 周 (1 week = 7 * 24 * 60 = 10080 minutes)
        long weeks = remaining / 10080;
        if (weeks > 0) {
            sb.append(weeks).append("周 ");
            remaining %= 10080;
        }

        // 天 (1 day = 24 * 60 = 1440 minutes)
        long days = remaining / 1440;
        if (days > 0) {
            sb.append(days).append("天 ");
            remaining %= 1440;
        }

        // 小时
        long hours = remaining / 60;
        if (hours > 0) {
            sb.append(hours).append("小时 ");
            remaining %= 60;
        }

        // 分钟
        if (remaining > 0) {
            sb.append(remaining).append("分钟");
        }

        return sb.toString().trim();
    }

    @Data
    public static class FieldValidationError {
        private final String field;
        private final String message;
    }
}

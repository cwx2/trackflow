package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.constant.RoleTypes;
import com.trackflow.common.constant.UserStatus;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动作配置校验器 —— 校验 action_config JSON 是否符合 schema 规则，
 * 并验证引用实体（用户、角色）的存在性和有效性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionConfigValidator {

    private static final Set<String> VALID_STRATEGIES = Set.of(
            "specific_user", "role_based", "previous_assignee", "reporter", "project_lead"
    );

    private static final Set<String> VALID_MODES = Set.of("round_robin", "least_loaded", "weighted_round_robin");

    private final ObjectMapper objectMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final ProjectMemberMapper projectMemberMapper;

    /**
     * 校验 action_config 结构。
     *
     * @param actionConfig 原始 Map 格式的配置（来自前端 JSON）
     * @return 错误消息列表，空列表表示校验通过
     */
    public List<String> validate(Map<String, Object> actionConfig) {
        return validate(actionConfig, "auto_assign");
    }

    /**
     * 校验 action_config 结构（根据动作类型分别校验）。
     *
     * @param actionConfig 原始 Map 格式的配置（来自前端 JSON）
     * @param actionType   动作类型
     * @return 错误消息列表，空列表表示校验通过
     */
    public List<String> validate(Map<String, Object> actionConfig, String actionType) {
        List<String> errors = new ArrayList<>();

        if (actionConfig == null || actionConfig.isEmpty()) {
            // add_comment 允许空 config（使用默认模板）
            if ("add_comment".equals(actionType)) {
                return errors;
            }
            errors.add("action_config 不能为空");
            return errors;
        }

        // 非 auto_assign 类型有各自的校验逻辑
        switch (actionType) {
            case "auto_assign" -> validateAutoAssignConfig(actionConfig, errors);
            case "add_comment" -> {
                // add_comment：comment_template 可选，无必填字段
            }
            case "add_tag" -> {
                Object tagName = actionConfig.get("tag_name");
                Object tagId = actionConfig.get("tag_id");
                if ((tagName == null || tagName.toString().isBlank()) && tagId == null) {
                    errors.add("add_tag 动作必须提供 tag_name 或 tag_id");
                }
            }
            case "require_field" -> {
                Object fieldId = actionConfig.get("required_field_id");
                if (fieldId == null) {
                    errors.add("require_field 动作必须提供 required_field_id");
                }
            }
            default -> {
                // 未知类型不做额外校验（actionType 合法性由上层 Service 验证）
            }
        }

        return errors;
    }

    /**
     * auto_assign 动作配置的结构校验
     */
    private void validateAutoAssignConfig(Map<String, Object> actionConfig, List<String> errors) {
        // 校验 strategy 字段
        Object strategyObj = actionConfig.get("strategy");
        if (strategyObj == null || strategyObj.toString().isBlank()) {
            errors.add("strategy 字段必须存在且不能为空");
            return;
        }

        String strategy = strategyObj.toString();
        if (!VALID_STRATEGIES.contains(strategy)) {
            errors.add("strategy 必须是以下值之一: " + VALID_STRATEGIES);
            return;
        }

        // 按策略类型校验附加字段
        switch (strategy) {
            case "specific_user" -> {
                Object userId = actionConfig.get("user_id");
                if (userId == null) {
                    errors.add("strategy 为 specific_user 时，user_id 不能为空");
                }
            }
            case "role_based" -> {
                Object roleId = actionConfig.get("role_id");
                if (roleId == null) {
                    errors.add("strategy 为 role_based 时，role_id 不能为空");
                }
                Object mode = actionConfig.get("mode");
                if (mode == null || mode.toString().isBlank()) {
                    errors.add("strategy 为 role_based 时，mode 不能为空");
                } else if (!VALID_MODES.contains(mode.toString())) {
                    errors.add("mode 必须是以下值之一: " + VALID_MODES);
                }
            }
            // previous_assignee, reporter, project_lead 无额外必填字段
        }

        // 校验 fallback_strategy（如果提供）
        Object fallback = actionConfig.get("fallback_strategy");
        if (fallback != null && !fallback.toString().isBlank()) {
            if (!VALID_STRATEGIES.contains(fallback.toString())) {
                errors.add("fallback_strategy 必须是以下值之一: " + VALID_STRATEGIES);
            }
        }
    }

    /**
     * 校验 action_config 中引用实体的存在性和有效性。
     * <p>
     * 必须在结构校验（validate）通过后调用。验证内容：
     * <ul>
     *   <li>specific_user：user_id 对应用户存在且活跃；项目级动作还要验证是项目成员</li>
     *   <li>role_based：role_id 对应角色存在且为 project 类型</li>
     * </ul>
     *
     * @param actionConfig 原始 Map 格式的配置
     * @param projectId    项目 ID（null 表示全局动作）
     * @return 错误消息列表，空列表表示校验通过
     */
    public List<String> validateEntityExistence(Map<String, Object> actionConfig, Long projectId) {
        return validateEntityExistence(actionConfig, projectId, "auto_assign");
    }

    /**
     * 校验 action_config 中引用实体的存在性和有效性（根据动作类型）。
     *
     * @param actionConfig 原始 Map 格式的配置
     * @param projectId    项目 ID（null 表示全局动作）
     * @param actionType   动作类型
     * @return 错误消息列表，空列表表示校验通过
     */
    public List<String> validateEntityExistence(Map<String, Object> actionConfig, Long projectId, String actionType) {
        List<String> errors = new ArrayList<>();

        if (actionConfig == null || actionConfig.isEmpty()) {
            return errors;
        }

        // 只有 auto_assign 类型需要校验引用实体
        if (!"auto_assign".equals(actionType)) {
            return errors;
        }

        Object strategyObj = actionConfig.get("strategy");
        if (strategyObj == null) {
            return errors;
        }
        String strategy = strategyObj.toString();

        switch (strategy) {
            case "specific_user" -> validateSpecificUser(actionConfig, projectId, errors);
            case "role_based" -> validateRoleBased(actionConfig, errors);
        }

        // 如果 fallback_strategy 也是 specific_user 或 role_based，也要校验其引用
        Object fallback = actionConfig.get("fallback_strategy");
        if (fallback != null && !fallback.toString().isBlank()) {
            String fallbackStrategy = fallback.toString();
            if ("specific_user".equals(fallbackStrategy)) {
                Object fallbackUserId = actionConfig.get("fallback_user_id");
                if (fallbackUserId != null) {
                    validateUserExists(parseLong(fallbackUserId), projectId, errors, "fallback_user_id");
                }
            } else if ("role_based".equals(fallbackStrategy)) {
                Object fallbackRoleId = actionConfig.get("fallback_role_id");
                if (fallbackRoleId != null) {
                    validateRoleExists(parseLong(fallbackRoleId), errors, "fallback_role_id");
                }
            }
        }

        return errors;
    }

    /**
     * 校验 specific_user 策略的 user_id 有效性
     */
    private void validateSpecificUser(Map<String, Object> actionConfig, Long projectId, List<String> errors) {
        Object userIdObj = actionConfig.get("user_id");
        if (userIdObj == null) {
            return; // 结构校验已处理
        }
        Long userId = parseLong(userIdObj);
        if (userId == null) {
            errors.add("user_id 格式无效，必须为数字");
            return;
        }
        validateUserExists(userId, projectId, errors, "user_id");
    }

    /**
     * 校验用户存在性、活跃性、项目成员关系
     */
    private void validateUserExists(Long userId, Long projectId, List<String> errors, String fieldName) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            errors.add(fieldName + " 对应的用户不存在（ID: " + userId + "）");
            return;
        }
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            errors.add(fieldName + " 对应的用户已禁用（用户: " + user.getUsername() + "）");
            return;
        }
        // 如果是项目级动作，验证用户是该项目的成员
        if (projectId != null) {
            boolean isMember = projectMemberMapper.exists(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getUserId, userId)
            );
            if (!isMember) {
                errors.add(fieldName + " 对应的用户不是该项目的成员（用户: " + user.getUsername() + "）");
            }
        }
    }

    /**
     * 校验 role_based 策略的 role_id 有效性
     */
    private void validateRoleBased(Map<String, Object> actionConfig, List<String> errors) {
        Object roleIdObj = actionConfig.get("role_id");
        if (roleIdObj == null) {
            return; // 结构校验已处理
        }
        Long roleId = parseLong(roleIdObj);
        if (roleId == null) {
            errors.add("role_id 格式无效，必须为数字");
            return;
        }
        validateRoleExists(roleId, errors, "role_id");
    }

    /**
     * 校验角色存在性和类型
     */
    private void validateRoleExists(Long roleId, List<String> errors, String fieldName) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            errors.add(fieldName + " 对应的角色不存在（ID: " + roleId + "）");
            return;
        }
        if (!RoleTypes.PROJECT.equals(role.getRoleType())) {
            errors.add(fieldName + " 必须引用项目级角色（role_type='project'），当前角色 '"
                    + role.getName() + "' 为 " + role.getRoleType() + " 类型");
        }
    }

    /**
     * 安全地将 Object 转为 Long（支持 Integer、Long、String 等类型）
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将 JSONB 字符串反序列化为 ActionConfig 对象。
     *
     * @param json action_config JSONB 字符串
     * @return 解析后的 ActionConfig 对象，解析失败返回 null
     */
    public ActionConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ActionConfig.class);
        } catch (JsonProcessingException e) {
            log.error("action_config JSON 解析失败: {}", json, e);
            return null;
        }
    }
}

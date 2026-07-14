package com.trackflow.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.workflow.dto.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动作配置校验器 —— 校验 action_config JSON 是否符合 schema 规则。
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

    /**
     * 校验 action_config 结构。
     *
     * @param actionConfig 原始 Map 格式的配置（来自前端 JSON）
     * @return 错误消息列表，空列表表示校验通过
     */
    public List<String> validate(Map<String, Object> actionConfig) {
        List<String> errors = new ArrayList<>();

        if (actionConfig == null || actionConfig.isEmpty()) {
            errors.add("action_config 不能为空");
            return errors;
        }

        // 校验 strategy 字段
        Object strategyObj = actionConfig.get("strategy");
        if (strategyObj == null || strategyObj.toString().isBlank()) {
            errors.add("strategy 字段必须存在且不能为空");
            return errors;
        }

        String strategy = strategyObj.toString();
        if (!VALID_STRATEGIES.contains(strategy)) {
            errors.add("strategy 必须是以下值之一: " + VALID_STRATEGIES);
            return errors;
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

        return errors;
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

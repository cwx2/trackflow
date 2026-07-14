package com.trackflow.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 更新转换动作 DTO（部分更新，仅非 null 字段生效）
 */
@Data
public class UpdateTransitionActionDTO {

    /** 动作类型 */
    private String actionType;

    /** 动作配置（JSON 结构） */
    private Map<String, Object> actionConfig;

    /** 执行顺序 */
    private Integer sortOrder;

    /** 是否启用 */
    private Boolean enabled;
}

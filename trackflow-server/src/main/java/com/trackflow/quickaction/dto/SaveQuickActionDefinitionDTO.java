package com.trackflow.quickaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建/更新快捷动作定义 DTO
 */
@Data
public class SaveQuickActionDefinitionDTO {

    private Long projectId;

    @NotBlank(message = "动作标识不能为空")
    private String actionKey;

    @NotBlank(message = "动作名称不能为空")
    private String label;

    private String icon;

    private Integer sortOrder;

    @NotNull(message = "表单配置不能为空")
    private String formSchema;

    @NotNull(message = "操作列表不能为空")
    private String actions;

    private String visibility;

    private String statusTransitionTo;

    private Boolean enabled;
}

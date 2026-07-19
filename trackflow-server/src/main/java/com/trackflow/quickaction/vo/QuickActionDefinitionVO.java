package com.trackflow.quickaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 快捷动作定义 VO（返回给前端）
 */
@Data
public class QuickActionDefinitionVO {

    private String id;

    private String projectId;

    private String actionKey;

    private String label;

    private String icon;

    private Integer sortOrder;

    /** 表单字段配置 JSON 字符串 */
    private String formSchema;

    /** 可执行操作列表 JSON 字符串 */
    private String actions;

    /** 可见性规则 JSON 字符串 */
    private String visibility;

    /** 自动化执行动作列表 JSON 字符串 */
    private String executionActions;

    /** 动作类型：form=需用户填表, rule=点击即执行 */
    private String actionType;

    /** 执行后触发的目标状态名称 */
    private String statusTransitionTo;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

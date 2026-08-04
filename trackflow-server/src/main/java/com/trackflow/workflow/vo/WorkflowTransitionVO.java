package com.trackflow.workflow.vo;

import lombok.Data;

/**
 * 工作流转换规则 VO
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class WorkflowTransitionVO {
    private String id;
    private String projectId;
    private String issueType;
    private String roleId;
    private String oldStatusId;
    private String newStatusId;
    private Boolean author;
    private Boolean assignee;
    private Boolean requireComment;

    /**
     * 守卫条件（JSONB 格式）。
     * 格式：{"conditions": [{"field": "assignee_id", "operator": "is_not_empty"}]}
     * 空对象 {} 或 null 表示无守卫条件限制。
     */
    private String conditions;

    /** 转换显示名（如"开始处理"），为空时前端使用目标状态名 */
    private String transitionName;

    /** 是否标记为初始状态 */
    private Boolean isInitial;
}

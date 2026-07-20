package com.trackflow.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流定义 VO（返回给前端）
 */
@Data
public class WorkflowDefinitionVO {

    private String id;

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 是否为系统默认工作流 */
    private Boolean isDefault;

    /** 转换规则数量 */
    private Integer transitionCount;

    /** 绑定的项目数量 */
    private Integer projectCount;

    /** 绑定的项目列表（简要信息） */
    private List<BoundProject> projects;

    /** 创建者 ID */
    private String createdBy;

    /** 创建者名称 */
    private String createdByName;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    @Data
    public static class BoundProject {
        private String id;
        private String name;
        private String key;
    }
}

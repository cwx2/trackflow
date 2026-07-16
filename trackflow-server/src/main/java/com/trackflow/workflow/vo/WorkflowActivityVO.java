package com.trackflow.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流变更审计日志 VO
 */
@Data
public class WorkflowActivityVO {

    private String id;

    /** 项目ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 工单类型 */
    private String issueType;

    /** 角色ID */
    private String roleId;

    /** 角色名称 */
    private String roleName;

    /** 操作用户ID */
    private String userId;

    /** 操作用户显示名称 */
    private String userDisplayName;

    /** 操作类型 */
    private String action;

    /** 人类可读的变更摘要 */
    private String summary;

    /** 新增的转换列表 */
    private List<TransitionChangeItem> added;

    /** 删除的转换列表 */
    private List<TransitionChangeItem> removed;

    /** 操作时间 */
    private LocalDateTime createdAt;

    /**
     * 单条转换变更项
     */
    @Data
    public static class TransitionChangeItem {
        /** 源状态名称 */
        private String fromStatus;
        /** 目标状态名称 */
        private String toStatus;
    }
}

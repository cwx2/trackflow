package com.trackflow.sprint.vo;

import lombok.Data;

/**
 * Sprint 创建预览 — 告知前端当前项目是否有活跃 Sprint 中未完成的工单，
 * 以便前端决定是否显示"移入未完成工单"复选框。
 */
@Data
public class CreationPreviewVO {

    /**
     * 当前活跃 Sprint 的 ID（无活跃 Sprint 时为 null）
     */
    private String activeSprintId;

    /**
     * 当前活跃 Sprint 的名称
     */
    private String activeSprintName;

    /**
     * 当前活跃 Sprint 中未关闭的工单数量
     */
    private int unresolvedIssueCount;

    /**
     * 该项目当前是否已设置默认 Sprint
     */
    private boolean hasDefaultSprint;

    /**
     * 当前默认 Sprint 的名称（如有）
     */
    private String defaultSprintName;
}

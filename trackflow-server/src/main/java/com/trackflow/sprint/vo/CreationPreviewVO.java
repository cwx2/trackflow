package com.trackflow.sprint.vo;

import lombok.Data;

/**
 * Sprint 创建预览 — 告知前端当前项目是否有含未完成工单的源 Sprint，
 * 以便前端决定是否显示"移入未完成工单"复选框。
 *
 * 源 Sprint 选取逻辑（对标 YouTrack）：
 * 1. 优先选活跃（ACTIVE）Sprint
 * 2. 无活跃时选最近完成（COMPLETED）且仍有未关闭工单的 Sprint
 * 3. 都没有则不显示该选项
 */
@Data
public class CreationPreviewVO {

    /**
     * 源 Sprint 的 ID（即未完成工单所属的 Sprint，无匹配时为 null）
     */
    private String sourceSprintId;

    /**
     * 源 Sprint 的名称
     */
    private String sourceSprintName;

    /**
     * 源 Sprint 的状态（"active" 或 "completed"），前端据此显示不同文案
     */
    private String sourceSprintStatus;

    /**
     * 源 Sprint 中未关闭的工单数量
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

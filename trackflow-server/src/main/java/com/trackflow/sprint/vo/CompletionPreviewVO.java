package com.trackflow.sprint.vo;

import lombok.Data;

import java.util.List;

/**
 * Sprint 完成预览 VO：展示未完成工单列表和可迁移的目标 Sprint
 */
@Data
public class CompletionPreviewVO {

    /** Sprint 中的工单总数（未删除） */
    private int totalIssues;

    /** 已完成（已关闭）的工单数 */
    private int completedIssues;

    /** 未完成工单列表 */
    private List<OpenIssueItem> openIssues;

    /** 可迁移的目标 Sprint 列表（当前项目中 planned/active 状态的 Sprint，排除自身） */
    private List<TargetSprintItem> targetSprints;

    @Data
    public static class OpenIssueItem {
        private String id;
        private String issueKey;
        private String title;
        private String priority;
        private String statusName;
        private String statusColor;
        private String assigneeName;
    }

    @Data
    public static class TargetSprintItem {
        private String id;
        private String name;
        private String status;
    }
}

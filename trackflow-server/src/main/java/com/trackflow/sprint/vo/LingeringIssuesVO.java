package com.trackflow.sprint.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 项目级遗留工单预览 VO：展示所有已完成 Sprint 中仍未关闭的工单，
 * 以及可迁移的目标 Sprint 列表。
 */
@Data
public class LingeringIssuesVO {

    /** 遗留工单列表 */
    private List<LingeringIssueItem> issues;

    /** 可迁移的目标 Sprint 列表（当前项目中 planned/active 状态的 Sprint） */
    private List<TargetSprintItem> targetSprints;

    @Data
    public static class LingeringIssueItem {
        private String id;
        private String issueKey;
        private String title;
        private String priority;
        private String priorityColor;
        private String statusName;
        private String statusColor;
        private String assigneeName;
        private LocalDate dueDate;
        private BigDecimal estimatedHours;
        /** 工单当前所属 Sprint 名称 */
        private String sprintName;
        /** 工单当前所属 Sprint ID */
        private String sprintId;
    }

    @Data
    public static class TargetSprintItem {
        private String id;
        private String name;
        private String status;
    }
}

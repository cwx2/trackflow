package com.trackflow.report.vo;

import lombok.Data;

import java.util.List;

/**
 * 时间报表多维视图 VO — 支持 Per Issue / Per User / Per Work Item 三种视图
 */
@Data
public class TimeReportGroupedVO {

    /** 当前视图类型：issue / user / work_item */
    private String viewType;

    /** 总工时（分钟） */
    private int totalMinutes;

    /** 总条数（用于分页） */
    private long totalCount;

    /** 当前页码 */
    private int page;

    /** 每页大小 */
    private int pageSize;

    /** Per Issue 视图：按工单分组 */
    private List<IssueGroupItem> issueGroups;

    /** Per User 视图：按用户分组 */
    private List<UserGroupItem> userGroups;

    /** Per Work Item 视图：每条工时记录明细 */
    private List<WorkItemDetail> workItems;

    // ─── Per Issue 视图 ──────────────────────────────────────────────────

    @Data
    public static class IssueGroupItem {
        /** 工单 ID (String 避免 JS 精度丢失) */
        private String issueId;
        /** 工单编号 */
        private String issueKey;
        /** 工单标题 */
        private String title;
        /** 所属项目名称 */
        private String projectName;
        /** 当前状态 */
        private String statusName;
        /** 总工时（分钟） */
        private int totalMinutes;
        /** 工时记录数量 */
        private int entryCount;
        /** 该工单下的明细（展开用，可选） */
        private List<WorkItemDetail> entries;
    }

    // ─── Per User 视图 ──────────────────────────────────────────────────

    @Data
    public static class UserGroupItem {
        /** 用户 ID (String) */
        private String userId;
        /** 用户显示名 */
        private String userName;
        /** 总工时（分钟） */
        private int totalMinutes;
        /** 工时记录数量 */
        private int entryCount;
        /** 各项目工时分布（按项目分组） */
        private List<ProjectSummaryItem> byProject;
        /** 该用户的明细（展开用，可选） */
        private List<WorkItemDetail> entries;
    }

    @Data
    public static class ProjectSummaryItem {
        private String projectName;
        private int minutes;
    }

    // ─── Per Work Item 视图 ──────────────────────────────────────────────

    @Data
    public static class WorkItemDetail {
        /** 时间记录 ID (String) */
        private String entryId;
        /** 工作日期 */
        private String workDate;
        /** 用户名 */
        private String userName;
        /** 工单编号 */
        private String issueKey;
        /** 工单标题 */
        private String issueTitle;
        /** 所属项目名称 */
        private String projectName;
        /** 工作类型 */
        private String workType;
        /** 工时（分钟） */
        private int minutes;
        /** 描述 */
        private String description;
    }
}

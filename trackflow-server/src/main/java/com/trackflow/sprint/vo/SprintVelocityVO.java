package com.trackflow.sprint.vo;

import lombok.Data;

import java.util.List;

/**
 * Sprint 速率统计 VO
 * <p>
 * 聚合项目近期已完成 Sprint 的速率数据，用于规划页参考：
 * - 每个已完成 Sprint 的工时完成量（velocity）
 * - 平均速率
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class SprintVelocityVO {

    /** 用于统计的 Sprint 数量 */
    private int sprintCount;

    /** 最近已完成 Sprint 的速率列表（时间从旧到新） */
    private List<SprintVelocityItem> sprints;

    /** 平均速率（已完成工时/Sprint，单位：小时） */
    private double averageVelocity;

    /** 最近一个 Sprint 的速率（最近的参考值，单位：小时） */
    private double lastVelocity;

    @Data
    public static class SprintVelocityItem {
        /** Sprint ID */
        private String id;
        /** Sprint 名称 */
        private String name;
        /** 开始日期 */
        private String startDate;
        /** 结束日期 */
        private String endDate;
        /** 该 Sprint 完成的工时（已关闭工单的 estimated_hours 之和，单位：小时） */
        private double completedHours;
        /** 该 Sprint 规划的总工时（所有工单的 estimated_hours 之和，单位：小时） */
        private double plannedHours;
        /** 工单总数 */
        private int totalIssues;
        /** 已完成工单数 */
        private int doneIssues;
    }
}

package com.trackflow.report.vo;

import lombok.Data;

import java.util.List;

/**
 * 时间报表 VO — 汇总工时数据
 * 支持按人员/项目/工作类型分组
 */
@Data
public class TimeReportVO {

    /** 汇总总工时（分钟） */
    private int totalMinutes;

    /** 按人员分组 */
    private List<GroupItem> byUser;

    /** 按项目分组 */
    private List<GroupItem> byProject;

    /** 按工作类型分组 */
    private List<GroupItem> byWorkType;

    /** 每日工时趋势 */
    private List<String> trendDates;
    private List<Integer> trendMinutes;

    /** 交叉维度：每个项目中每个人的工时 */
    private List<CrossDimensionItem> crossProjectUser;

    @Data
    public static class GroupItem {
        private String name;
        private int minutes;
        /** 占比（百分比，保留1位小数） */
        private double percentage;
    }

    @Data
    public static class CrossDimensionItem {
        private String projectName;
        private String userName;
        private int minutes;
    }
}

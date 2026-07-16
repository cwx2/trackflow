package com.trackflow.report.vo;

import lombok.Data;

import java.util.List;

/**
 * 项目对比数据 VO — 跨项目统计对比
 */
@Data
public class ProjectComparisonVO {
    private List<ProjectStatItem> items;

    @Data
    public static class ProjectStatItem {
        /** 项目名称 */
        private String name;
        /** 项目 Key */
        private String key;
        /** 工单总数 */
        private long total;
        /** 进行中 */
        private long open;
        /** 已完成 */
        private long closed;
        /** 完成率（百分比） */
        private long completionRate;
        /** 已逾期 */
        private long overdue;
    }
}

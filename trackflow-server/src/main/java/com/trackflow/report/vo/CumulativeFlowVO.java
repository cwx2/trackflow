package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

/**
 * 累积流图（Cumulative Flow Diagram）数据 VO
 * X 轴为日期，每个状态一个堆叠面积区域
 */
@Data
public class CumulativeFlowVO {
    /** 日期序列（ISO 格式：2026-07-01） */
    private List<String> dates;
    /** 各状态的时间序列数据 */
    private List<StatusSeries> series;

    @Data
    public static class StatusSeries {
        /** 状态名称 */
        private String name;
        /** 状态颜色 */
        private String color;
        /** 每天该状态的工单数量（与 dates 等长） */
        private List<Long> data;
    }
}

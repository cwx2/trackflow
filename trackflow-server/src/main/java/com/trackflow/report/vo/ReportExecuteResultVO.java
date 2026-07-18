package com.trackflow.report.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报表执行结果 VO
 * 对应 GET /api/v1/reports/{id}/data 的返回数据
 *
 * 单维度模式：labels + data（一维数组）
 * 双维度模式：labels（主维度标签） + secondLabels（第二维度标签） + matrix（二维数组）
 */
@Data
public class ReportExecuteResultVO {

    /** 报表标题 */
    private String title;

    /** 图表类型（bar_horizontal/bar_vertical/pie/line/number_card） */
    private String chartType;

    /** 报表数据类型（来自 ReportType） */
    private String type;

    /** 主分组依据（status/priority/type/assignee/project） */
    private String groupBy;

    /** 第二分组依据（可选，双维度交叉时使用） */
    private String secondGroupBy;

    /** 主维度分组标签列表 */
    private List<String> labels;

    /** 各分组的统计数值（单维度模式） */
    private List<Long> data;

    /** 第二维度标签列表（双维度模式） */
    private List<String> secondLabels;

    /**
     * 矩阵数据（双维度模式）
     * matrix[i][j] = 主维度第i项 × 第二维度第j项的计数
     */
    private List<List<Long>> matrix;

    /** 工单总数 */
    private long total;

    /** 应用的筛选条件摘要（方便前端展示） */
    private Map<String, Object> appliedFilters;
}

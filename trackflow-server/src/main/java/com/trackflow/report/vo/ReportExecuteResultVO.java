package com.trackflow.report.vo;

import lombok.Data;

import java.util.List;

/**
 * 报表执行结果 VO
 * 对应 GET /api/v1/reports/{id}/data 的返回数据
 */
@Data
public class ReportExecuteResultVO {

    /** 报表标题 */
    private String title;

    /** 图表类型（bar/pie/line） */
    private String type;

    /** 分组依据（status/priority/type/assignee） */
    private String groupBy;

    /** 分组标签列表 */
    private List<String> labels;

    /** 各分组的统计数值 */
    private List<Long> data;

    /** 工单总数 */
    private long total;
}

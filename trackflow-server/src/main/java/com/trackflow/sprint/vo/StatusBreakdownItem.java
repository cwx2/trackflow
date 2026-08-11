package com.trackflow.sprint.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sprint 中按实际工作流状态逐一统计的工单数量。
 * 用于进度条多段着色和按状态计数展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusBreakdownItem {
    /** 状态 ID（字符串，前端使用） */
    private String statusId;
    /** 状态名称 */
    private String statusName;
    /** 状态颜色 */
    private String statusColor;
    /** 状态类别（open / in_progress / done / cancelled） */
    private String category;
    /** 该状态的工单数量 */
    private int count;
    /** 排序序号（对应 issue_status.sort_order） */
    private int sortOrder;
}

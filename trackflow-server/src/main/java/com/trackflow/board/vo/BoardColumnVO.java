package com.trackflow.board.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 看板列配置 VO —— 返回给前端的列配置信息
 */
@Data
public class BoardColumnVO {

    /** 状态 ID */
    private String statusId;

    /** 通用列标识值（status模式=statusId, priority模式=Critical/High/Normal/Low） */
    private String fieldValue;

    /** 状态名称 */
    private String statusName;

    /** 状态代码 */
    private String statusCode;

    /** 状态颜色 */
    private String statusColor;

    /** 状态分类 (open/in_progress/done/cancelled) */
    private String statusCategory;

    /** 是否在看板中可见 */
    private Boolean visible;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 是否默认折叠 */
    private Boolean collapsed;

    /** 最小在制品数量（低于此数时列标题显示黄色警告），null 表示不限制 */
    private Integer wipMin;

    /** 最大在制品数量（超过此数时列标题显示红色警告），null 表示不限制 */
    private Integer wipMax;

    /**
     * 该隐藏列中是否有工单存在。
     * 仅当 visible=false 且有工单使用该状态时为 true，用于前端提示。
     */
    private Boolean hasHiddenIssues;

    /** 该项目中处于此状态的工单数量 */
    private Integer issueCount;

    /** 该列所有工单的预估工时总和（estimated_hours 聚合） */
    private BigDecimal totalEstimation;

    /** 该状态是否出现在项目的工作流转换路径中（即是工作流图中的节点） */
    private Boolean inWorkflow;
}

package com.trackflow.board.vo;

import lombok.Data;

/**
 * 看板列配置 VO —— 返回给前端的列配置信息
 */
@Data
public class BoardColumnVO {

    /** 状态 ID */
    private String statusId;

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
}

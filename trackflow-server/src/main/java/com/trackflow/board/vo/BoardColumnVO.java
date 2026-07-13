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
}

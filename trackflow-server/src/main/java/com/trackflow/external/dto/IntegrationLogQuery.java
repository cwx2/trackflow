package com.trackflow.external.dto;

import lombok.Data;

/**
 * 集成日志查询条件
 */
@Data
public class IntegrationLogQuery {

    /** 适配器类型筛选 */
    private String adapterType;

    /** 状态筛选 */
    private String status;

    /** 开始时间（ISO 格式） */
    private String startTime;

    /** 结束时间（ISO 格式） */
    private String endTime;

    /** 页码 */
    private int page = 1;

    /** 每页条数 */
    private int pageSize = 20;
}

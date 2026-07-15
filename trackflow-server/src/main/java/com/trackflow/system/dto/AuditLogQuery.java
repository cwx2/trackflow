package com.trackflow.system.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 审计日志查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQuery extends PageQuery {

    /** 操作类型筛选 */
    private String action;

    /** 目标类型筛选 */
    private String targetType;

    /** 操作者 ID 筛选 */
    private Long operatorId;

    /** 目标 ID 筛选 */
    private Long targetId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;
}

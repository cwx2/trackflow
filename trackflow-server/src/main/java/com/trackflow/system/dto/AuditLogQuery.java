package com.trackflow.system.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.Set;

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

    /**
     * 文本搜索关键词。
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li>键值对语法：<code>author:xxx</code>（按操作者名称）、<code>target:xxx</code>（按目标名称）</li>
     *   <li>纯文本：对操作者名称和目标名称做模糊匹配</li>
     * </ul>
     */
    private String search;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of(
                "id", "action", "target_type", "operator_id",
                "target_id", "created_at"
        );
    }
}

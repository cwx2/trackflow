package com.trackflow.issue.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.issue.entity.Issue;

/**
 * Issue 优先级排序工具 — 提供全局唯一的优先级语义排序表达式。
 *
 * <p>排序完全依赖数据库 {@code custom_field_option.position} 字段：
 * 通过 issue.priority_option_id 关联 custom_field_option 表取得 position。
 * 当 priority_option_id 为 NULL（历史数据未回填）时，使用 position=99 排末尾。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public final class IssuePriorityHelper {

    private IssuePriorityHelper() {
        // 工具类禁止实例化
    }

    /**
     * 未知优先级的默认排序位置（排在所有已知优先级之后）。
     */
    public static final int UNKNOWN_PRIORITY_POSITION = 99;

    /**
     * 优先级语义排序 CASE 表达式（SQL 片段）。
     *
     * <p>使用 priority_option_id 列关联 custom_field_option.position 排序。
     * 当 priority_option_id 为 null 时（历史数据未迁移），回退到 99（排末尾）。</p>
     */
    public static final String PRIORITY_ORDER_EXPR =
            "COALESCE((SELECT position FROM custom_field_option WHERE id = priority_option_id), "
            + UNKNOWN_PRIORITY_POSITION + ")";

    /**
     * 对 QueryWrapper 应用优先级排序（作为唯一排序条件，附带 updated_at 作为次级排序）。
     *
     * <p>适用于：需要把优先级排序 + 二级排序作为最终 ORDER BY 直接拼接的场景。
     * 例如 DashboardService 的"分配给我的工单"列表。</p>
     *
     * @param wrapper    QueryWrapper 实例
     * @param descPriority true=高优先级在前（Critical→Low），false=低优先级在前（Low→Critical）
     * @param limit      结果限制行数，传 0 或负数表示不限制
     */
    public static void applyPrioritySortWithLimit(QueryWrapper<Issue> wrapper, boolean descPriority, int limit) {
        StringBuilder sb = new StringBuilder("ORDER BY ");
        sb.append(PRIORITY_ORDER_EXPR);
        // descPriority=true 意味着高优先级在前 → 数值 ASC（position 0=最高优先级 排在前面）
        sb.append(descPriority ? " ASC" : " DESC");
        sb.append(", updated_at DESC");
        if (limit > 0) {
            sb.append(" LIMIT ").append(limit);
        }
        wrapper.last(sb.toString());
    }

    /**
     * 对 QueryWrapper 应用优先级排序（作为唯一排序条件，附带 updated_at 作为次级排序）。
     *
     * <p>不带 LIMIT 版本，适用于分页查询场景。</p>
     *
     * @param wrapper    QueryWrapper 实例
     * @param descPriority true=高优先级在前（Critical→Low），false=低优先级在前（Low→Critical）
     */
    public static void applyPrioritySort(QueryWrapper<Issue> wrapper, boolean descPriority) {
        applyPrioritySortWithLimit(wrapper, descPriority, 0);
    }

    /**
     * 对 QueryWrapper 应用优先级排序作为 orderBy（不使用 .last()）。
     *
     * <p>适用于：已有其他 .last() 调用、或需要和其他排序条件组合的场景。
     * 此方法直接调用 orderByAsc/orderByDesc 传入 CASE 表达式。</p>
     *
     * @param wrapper    QueryWrapper 实例
     * @param descPriority true=高优先级在前（Critical→Low），false=低优先级在前（Low→Critical）
     */
    public static void applyPriorityOrderBy(QueryWrapper<Issue> wrapper, boolean descPriority) {
        if (descPriority) {
            // 高优先级在前 → 数值 ASC（position 0 排前面）
            wrapper.orderByAsc(PRIORITY_ORDER_EXPR);
        } else {
            // 低优先级在前 → 数值 DESC（position 高的排前面）
            wrapper.orderByDesc(PRIORITY_ORDER_EXPR);
        }
    }
}

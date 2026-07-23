package com.trackflow.board.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板聚合数据 VO — 返回已按列分组的工单数据。
 * <p>
 * 前端收到后无需客户端 filter，直接按列渲染。
 * 每列包含工单列表 + 统计信息（totalCount、totalEstimation）。
 * <p>
 * columnConfigs 附带本次数据所使用的列配置快照，确保列定义与工单数据原子一致，
 * 消除前端分步请求 getColumns + getBoardData 的竞态窗口。
 */
@Data
public class BoardDataVO {

    /** 各列数据（已按列配置的 sortOrder 排序） */
    private List<ColumnData> columns;

    /** 本次聚合所使用的完整列配置快照（含可见+隐藏列），前端直接使用此字段渲染列 */
    private List<BoardColumnVO> columnConfigs;

    /** 看板中工单总数（所有列加总，含折叠列） */
    private int totalIssueCount;

    /** 是否有截断（totalIssueCount 超过了返回的工单总数） */
    private boolean truncated;

    /**
     * 单列数据
     */
    @Data
    public static class ColumnData {
        /** 状态 ID */
        private String statusId;

        /** 状态名称（冗余，前端可直接使用） */
        private String statusName;

        /** 该列的工单列表（精简卡片数据，按 position/创建时间排序） */
        private List<BoardCardVO> issues;

        /** 该列的工单总数（可能大于 issues.size()，列级分页时使用） */
        private int totalCount;

        /** 该列的预估工时总和 */
        private BigDecimal totalEstimation;

        /** 该列是否折叠（折叠时 issues 为空列表，只有 totalCount 和 totalEstimation） */
        private boolean collapsed;
    }
}

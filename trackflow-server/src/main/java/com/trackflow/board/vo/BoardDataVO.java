package com.trackflow.board.vo;

import com.trackflow.issue.vo.IssueVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板聚合数据 VO — 返回已按列分组的工单数据。
 * <p>
 * 前端收到后无需客户端 filter，直接按列渲染。
 * 每列包含工单列表 + 统计信息（totalCount、totalEstimation）。
 */
@Data
public class BoardDataVO {

    /** 各列数据（已按列配置的 sortOrder 排序） */
    private List<ColumnData> columns;

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

        /** 该列的工单列表（按 position/创建时间排序） */
        private List<IssueVO> issues;

        /** 该列的工单总数（可能大于 issues.size()，列级分页时使用） */
        private int totalCount;

        /** 该列的预估工时总和 */
        private BigDecimal totalEstimation;

        /** 该列是否折叠（折叠时 issues 为空列表，只有 totalCount 和 totalEstimation） */
        private boolean collapsed;
    }
}

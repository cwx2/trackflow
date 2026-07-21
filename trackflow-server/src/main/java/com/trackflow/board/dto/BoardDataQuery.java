package com.trackflow.board.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 看板聚合数据查询参数
 */
@Data
public class BoardDataQuery {

    /** 项目 ID（必填） */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** Sprint ID（可选，筛选特定迭代的工单） */
    private Long sprintId;

    /** 负责人 ID（可选，筛选特定负责人的工单） */
    private Long assigneeId;

    /** 关键词搜索（可选，搜索标题/编号/负责人） */
    private String keyword;

    /** 排除在此日期之前完成的工单（ISO 日期格式 yyyy-MM-dd） */
    private LocalDate excludeDoneBefore;

    /** 每列最大工单数（默认 200，0=不限制） */
    private Integer columnLimit;
}

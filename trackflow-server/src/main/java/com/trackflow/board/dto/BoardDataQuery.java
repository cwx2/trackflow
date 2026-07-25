package com.trackflow.board.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

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

    /** 排除在此日期之前完成的工单（ISO 日期格式 yyyy-MM-dd，String 接收避免绑定问题） */
    private String excludeDoneBefore;

    /** 每列最大工单数（默认 200，0=不限制） */
    private Integer columnLimit;

    /**
     * 泳道分组字段（可选）。
     * 当前端配置了泳道值过滤时传入，用于服务端 SQL 追加泳道维度 WHERE 条件。
     * 支持：assignee / priority / type / sprint
     * 为 null 或 'none' 时不做泳道过滤。
     */
    private String swimlaneField;

    /**
     * 泳道选中值列表（可选）。
     * 与 swimlaneField 配合使用，表示只返回这些泳道对应的工单。
     * 仅当 showUncategorized=false 时传入（showUncategorized=true 时不传，返回全量工单供前端构建 Uncategorized 泳道）。
     */
    private List<String> swimlaneValues;

    /**
     * 将 excludeDoneBefore 字符串解析为 LocalDate。
     * 返回 null 表示无此参数或格式无效。
     */
    public LocalDate getExcludeDoneBeforeAsDate() {
        if (excludeDoneBefore == null || excludeDoneBefore.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(excludeDoneBefore);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 设置 excludeDoneBefore 为 LocalDate（服务端内部使用）
     */
    public void setExcludeDoneBeforeDate(LocalDate date) {
        this.excludeDoneBefore = date != null ? date.toString() : null;
    }
}

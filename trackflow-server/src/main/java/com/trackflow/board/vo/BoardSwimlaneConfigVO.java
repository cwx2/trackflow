package com.trackflow.board.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板泳道配置 VO。
 * @since 1.0
 */
@Data
public class BoardSwimlaneConfigVO {
    /** 泳道分组字段：none/assignee/priority/type/sprint/tag/parent */
    private String groupByField;
    /** 选中的泳道值列表，null 或空表示全选（向后兼容） */
    private List<String> selectedValues;
    /** 是否显示"未分类"泳道（默认 true） */
    private Boolean showUncategorized;
    /** 未分类泳道位置：top / bottom（默认 bottom） */
    private String uncategorizedPosition;
    /**
     * Issues 模式下作为泳道行的 Issue 类型（如 "Epic"、"Feature"）。
     * 仅当 groupByField = "parent" 时有效。
     */
    private String swimlaneIssueType;
}

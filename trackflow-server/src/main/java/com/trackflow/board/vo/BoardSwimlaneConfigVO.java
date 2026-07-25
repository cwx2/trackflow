package com.trackflow.board.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板泳道配置 VO。
 */
@Data
public class BoardSwimlaneConfigVO {
    /** 泳道分组字段：none/assignee/priority/type/sprint/tag */
    private String groupByField;
    /** 选中的泳道值列表，null 或空表示全选（向后兼容） */
    private List<String> selectedValues;
    /** 是否显示"未分类"泳道（默认 true） */
    private Boolean showUncategorized;
    /** 未分类泳道位置：top / bottom（默认 bottom） */
    private String uncategorizedPosition;
}

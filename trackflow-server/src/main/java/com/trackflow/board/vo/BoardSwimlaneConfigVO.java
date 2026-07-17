package com.trackflow.board.vo;

import lombok.Data;

/**
 * 看板泳道配置 VO。
 */
@Data
public class BoardSwimlaneConfigVO {
    /** 泳道分组字段：none/assignee/priority/type/sprint/tag */
    private String groupByField;
}

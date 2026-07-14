package com.trackflow.query.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询面板 VO — 左侧面板完整数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryPanelVO {
    private List<QueryPanelItemVO> pinned;
    private List<QueryPanelItemVO> queries;
}

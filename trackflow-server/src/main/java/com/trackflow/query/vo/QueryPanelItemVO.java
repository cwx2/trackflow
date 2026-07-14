package com.trackflow.query.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询面板单项 VO — 面板中每个查询的展示数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryPanelItemVO {
    private String id;
    private String name;
    private String folder;
    private Boolean pinned;
    private Boolean shared;
    private String userId;
    private long count;
}

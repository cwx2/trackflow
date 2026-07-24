package com.trackflow.issue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 侧边栏标签面板项 — 包含标签信息 + 匹配工单数量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagPanelItemVO {
    private String id;
    private String name;
    private String color;
    private String projectId;
    private long count;
}

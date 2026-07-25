package com.trackflow.issue.dto;

import lombok.Data;

/**
 * Mapper 查询结果对象 — 用户收藏标签及工单匹配数量
 * 替代原先的 Map&lt;String, Object&gt;，提供类型安全的字段访问
 */
@Data
public class TagFavoriteCountRow {

    /** 标签 ID */
    private Long tagId;

    /** 标签名称 */
    private String name;

    /** 标签颜色 */
    private String color;

    /** 所属项目 ID */
    private Long projectId;

    /** 匹配的未删除工单数量 */
    private Long issueCount;
}

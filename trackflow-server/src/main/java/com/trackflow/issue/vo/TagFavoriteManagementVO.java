package com.trackflow.issue.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 标签收藏管理 VO — 用于"管理收藏面板"接口返回
 * 包含标签信息及当前用户是否已收藏的标志
 */
@Data
@Builder
public class TagFavoriteManagementVO {

    /** 标签 ID（String 类型，避免 JS Long 精度丢失） */
    private String id;

    /** 标签名称 */
    private String name;

    /** 标签颜色 */
    private String color;

    /** 所属项目 ID（String 类型） */
    private String projectId;

    /** 当前用户是否已收藏 */
    private boolean favorited;
}

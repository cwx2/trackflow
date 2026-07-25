package com.trackflow.board.vo;

import lombok.Data;

/**
 * 看板列表项 VO - 用于 Board Selector 下拉面板
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class BoardListItemVO {
    /** 项目 ID (作为看板标识) */
    private String projectId;
    /** 看板名称 (来自 board_general_config.name 或项目名) */
    private String name;
    /** 项目 Key */
    private String projectKey;
    /** 项目名称 */
    private String projectName;
    /** 所有者显示名 */
    private String ownerName;
    /** 所有者 ID */
    private String ownerId;
    /** 是否已收藏 */
    private boolean favorite;
}

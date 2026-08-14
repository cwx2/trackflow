package com.trackflow.project.vo;

import lombok.Data;

/**
 * 项目收藏切换结果 VO
 */
@Data
public class FavoriteToggleVO {

    /** 当前收藏状态：true=已收藏，false=未收藏 */
    private Boolean favorited;

    public static FavoriteToggleVO of(boolean favorited) {
        FavoriteToggleVO vo = new FavoriteToggleVO();
        vo.setFavorited(favorited);
        return vo;
    }
}

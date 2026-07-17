package com.trackflow.board.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板卡片配置 VO —— 返回给前端的卡片配置信息
 */
@Data
public class BoardCardConfigVO {

    /** 卡片上显示的字段列表 */
    private List<String> visibleFields;

    /** 颜色方案：none / priority / type / project */
    private String colorScheme;
}

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

    /**
     * 当前估算字段 ID（String，避免 JS Long 精度丢失）。
     * 对应 YouTrack Board Settings Cards Tab 的 "Current estimation field"。
     * null 表示未配置。
     */
    private String currentEstimationFieldId;

    /**
     * 原始估算字段 ID（String，避免 JS Long 精度丢失）。
     * 对应 YouTrack Board Settings Cards Tab 的 "Original estimation field"。
     * null 表示未配置。
     */
    private String originalEstimationFieldId;
}

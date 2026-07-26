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

    /**
     * 每个字段的显示格式，key 为字段名，value 为 "full_name" 或 "initial"。
     * 示例：{"assignee":"initial","priority":"full_name"}
     * 对应 YouTrack Cards Tab 字段的 Display menu（Full name / Initial）。
     * null 或不含某字段表示该字段使用默认的 full_name 模式。
     */
    private java.util.Map<String, String> fieldDisplayModes;

    /**
     * 是否在卡片自定义字段值旁显示颜色指示器。
     * 对应 YouTrack Board Settings > Cards Tab 的 "Show colors for other custom fields" 开关。
     * 默认 true（与 YouTrack 行为一致）。
     */
    private Boolean showCustomFieldColors;
}

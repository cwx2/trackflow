package com.trackflow.board.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 更新看板卡片配置 DTO
 */
@Data
public class UpdateBoardCardConfigDTO {

    /** 乐观锁版本号（从 GET 响应中获取，用于并发冲突检测） */
    private Integer configVersion;

    /** 卡片上显示的字段列表（至少一个） */
    @NotEmpty(message = "显示字段不能为空")
    private List<String> visibleFields;

    /** 颜色方案 */
    @NotNull(message = "颜色方案不能为空")
    @Pattern(regexp = "^(none|priority|type|project)$", message = "颜色方案只能是 none/priority/type/project")
    private String colorScheme;

    /**
     * 当前估算字段 ID（引用 custom_field_definition），null 表示不配置。
     * 对应 YouTrack Board Settings Cards Tab 的 "Current estimation field"。
     */
    private Long currentEstimationFieldId;

    /**
     * 原始估算字段 ID（引用 custom_field_definition），null 表示不配置。
     * 对应 YouTrack Board Settings Cards Tab 的 "Original estimation field"。
     */
    private Long originalEstimationFieldId;

    /**
     * 每个字段的显示格式，key 为字段名，value 为 "full_name" 或 "initial"。
     * 示例：{"assignee":"initial","priority":"full_name"}
     * null 或缺失键表示该字段使用默认的 full_name 模式。
     * 对应 YouTrack Cards Tab 字段 Display menu。
     */
    private java.util.Map<String, String> fieldDisplayModes;

    /**
     * 是否在卡片自定义字段值旁显示颜色指示器。
     * 对应 YouTrack Board Settings > Cards Tab 的 "Show colors for other custom fields" 开关。
     * null 时使用默认值 true。
     */
    private Boolean showCustomFieldColors;
}

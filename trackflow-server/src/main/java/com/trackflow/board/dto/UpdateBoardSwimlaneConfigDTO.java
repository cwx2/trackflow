package com.trackflow.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 更新看板泳道配置 DTO。
 */
@Data
public class UpdateBoardSwimlaneConfigDTO {
    /** 乐观锁版本号（从 GET 响应中获取，用于并发冲突检测） */
    private Integer configVersion;

    /** 泳道分组字段：none/assignee/priority/type/sprint/tag/parent */
    @NotBlank(message = "分组字段不能为空")
    @Pattern(regexp = "^(none|assignee|priority|type|sprint|tag|parent)$",
            message = "分组字段必须是 none/assignee/priority/type/sprint/tag/parent 之一")
    private String groupByField;

    /** 选中的泳道值列表，null 或空表示全选 */
    private List<String> selectedValues;

    /** 是否显示"未分类"泳道（默认 true） */
    private Boolean showUncategorized;

    /** 未分类泳道位置：top / bottom（默认 bottom） */
    @Pattern(regexp = "^(top|bottom)$", message = "未分类泳道位置必须是 top 或 bottom")
    private String uncategorizedPosition;

    /**
     * Issues 模式下作为泳道行的 Issue 类型（如 "Epic"、"Feature"）。
     * 仅当 groupByField = "parent" 时有效。
     */
    private String swimlaneIssueType;
}

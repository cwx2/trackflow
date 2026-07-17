package com.trackflow.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新看板泳道配置 DTO。
 */
@Data
public class UpdateBoardSwimlaneConfigDTO {
    /** 泳道分组字段：none/assignee/priority/type/sprint/tag */
    @NotBlank(message = "分组字段不能为空")
    @Pattern(regexp = "^(none|assignee|priority|type|sprint|tag)$",
            message = "分组字段必须是 none/assignee/priority/type/sprint/tag 之一")
    private String groupByField;
}

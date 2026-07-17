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

    /** 卡片上显示的字段列表（至少一个） */
    @NotEmpty(message = "显示字段不能为空")
    private List<String> visibleFields;

    /** 颜色方案 */
    @NotNull(message = "颜色方案不能为空")
    @Pattern(regexp = "^(none|priority|type|project)$", message = "颜色方案只能是 none/priority/type/project")
    private String colorScheme;
}

package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新项目独立选项集请求 DTO
 */
@Data
public class UpdateProjectOptionsDTO {

    /**
     * 选项列表（按顺序）
     */
    @NotNull(message = "选项列表不能为空")
    private List<UpdateCustomFieldDTO.OptionItem> options;
}

package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 设置自定义字段选项排序模式的请求体
 */
@Data
public class SetSortModeDTO {

    @NotBlank(message = "排序模式不能为空")
    @Pattern(regexp = "^(manual|name_asc|name_desc|name_ci_asc|name_ci_desc)$",
            message = "排序模式必须为: manual / name_asc / name_desc / name_ci_asc / name_ci_desc")
    private String sortMode;
}

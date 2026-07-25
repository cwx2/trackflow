package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量更新自定义字段属性 DTO
 */
@Data
public class BatchUpdateCustomFieldDTO {

    @NotEmpty(message = "字段ID列表不能为空")
    private List<Long> ids;

    @NotBlank(message = "更新字段名不能为空")
    private String field;

    @NotNull(message = "更新值不能为空")
    private Boolean value;
}

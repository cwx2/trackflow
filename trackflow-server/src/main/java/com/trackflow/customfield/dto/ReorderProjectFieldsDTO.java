package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 项目级自定义字段排序 DTO
 */
@Data
public class ReorderProjectFieldsDTO {

    /** 字段 ID 列表，顺序即为排序结果 */
    @NotEmpty(message = "字段 ID 列表不能为空")
    private List<Long> fieldIds;
}

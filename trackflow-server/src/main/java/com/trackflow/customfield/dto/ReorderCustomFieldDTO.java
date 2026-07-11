package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReorderCustomFieldDTO {

    /** 按新顺序排列的字段 ID 列表 */
    @NotEmpty(message = "ID 列表不能为空")
    private List<Long> ids;
}

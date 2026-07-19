package com.trackflow.workitemattr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 属性值转移 DTO
 * 将所有引用 sourceValueId 的工时记录迁移到 targetValueId
 */
@Data
public class TransferAttributeValueDTO {

    @NotNull(message = "目标值ID不能为空")
    private Long targetValueId;
}

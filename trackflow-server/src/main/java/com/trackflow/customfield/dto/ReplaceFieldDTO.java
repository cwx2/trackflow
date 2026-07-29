package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 替换字段请求 DTO
 */
@Data
public class ReplaceFieldDTO {

    /**
     * 目标字段 ID（用于替换当前字段的字段）
     */
    @NotNull(message = "目标字段 ID 不能为空")
    private Long targetFieldId;
}

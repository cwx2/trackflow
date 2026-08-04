package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 值集合合并请求参数（将源字段的活跃选项合并到当前字段）
 */
@Data
public class MergeOptionsDTO {

    /**
     * 源字段 ID（从该字段复制活跃选项到当前字段）
     */
    @NotNull(message = "源字段ID不能为空")
    private Long sourceFieldId;
}

package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 项目级附加自定义字段 DTO
 */
@Data
public class AttachCustomFieldDTO {

    /** 要附加的自定义字段 ID */
    @NotNull(message = "字段 ID 不能为空")
    private Long customFieldId;
}

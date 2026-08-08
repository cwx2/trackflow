package com.trackflow.external.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用/禁用适配器请求 DTO
 */
@Data
public class ToggleAdapterDTO {

    @NotNull(message = "enabled 参数必须提供")
    private Boolean enabled;
}

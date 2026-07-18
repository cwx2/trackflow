package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 禁用用户请求 DTO
 */
@Data
public class DisableUserDTO {

    /**
     * 禁用状态分类
     * 允许值: banned / suspended / inactive / deactivated / locked
     */
    @NotBlank(message = "禁用状态不能为空")
    @Pattern(regexp = "^(banned|suspended|inactive|deactivated|locked)$",
            message = "禁用状态必须是: banned/suspended/inactive/deactivated/locked")
    private String banStatus;

    /**
     * 禁用原因说明（可选）
     */
    private String banReason;
}

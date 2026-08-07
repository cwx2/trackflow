package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 组织级访问授权 DTO
 */
@Data
public class GrantOrgAccessDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}

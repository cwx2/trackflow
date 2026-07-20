package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 全局项目角色分配请求 DTO
 */
@Data
public class GlobalMemberDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}

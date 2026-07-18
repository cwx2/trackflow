package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户组角色分配 DTO
 * <p>
 * projectId = null 表示分配全局角色
 * projectId != null 表示分配项目级角色
 */
@Data
public class GroupRoleDTO {

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    /** 项目 ID（null 表示全局范围） */
    private Long projectId;
}

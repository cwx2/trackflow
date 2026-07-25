package com.trackflow.system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 合并角色请求体 DTO
 * <p>
 * 将多个源角色合并到一个目标角色：
 * - 目标角色继承所有源角色的权限（并集）
 * - 所有源角色的用户/组分配被迁移到目标角色
 * - 源角色被删除
 * </p>
 */
@Data
public class MergeRolesDTO {

    /**
     * 源角色 ID 列表（将被合并删除的角色）
     */
    @NotEmpty(message = "源角色列表不能为空")
    private List<Long> sourceRoleIds;

    /**
     * 目标角色 ID（保留的角色，继承所有源角色权限和用户分配）
     */
    @NotNull(message = "目标角色ID不能为空")
    private Long targetRoleId;
}

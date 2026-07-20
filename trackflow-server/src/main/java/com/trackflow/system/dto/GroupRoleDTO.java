package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户组角色分配 DTO
 * <p>
 * 支持三种作用域模式：
 * 1. 全局角色（global role type）：projectIds 为空，globalScope=false（默认）
 * 2. 项目角色 - 全局作用域：globalScope=true，表示该项目角色对所有项目生效
 * 3. 项目角色 - 指定项目：projectIds 包含具体项目 ID
 */
@Data
public class GroupRoleDTO {

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    /**
     * 项目 ID 列表（用于批量分配到多个项目）
     * 为空或 null 时：
     * - 如果 globalScope=true，表示该项目角色对所有项目生效
     * - 如果是全局角色类型，正常分配全局角色
     */
    private List<Long> projectIds;

    /**
     * 是否全局作用域（所有项目）
     * 仅对 project 类型角色有意义。
     * true = 该项目角色对所有项目（含未来新建）生效
     */
    private Boolean globalScope;

    // ===== 兼容旧接口：单项目分配 =====

    /**
     * 单项目 ID（兼容旧接口，优先使用 projectIds）
     * @deprecated 推荐使用 projectIds
     */
    private Long projectId;
}

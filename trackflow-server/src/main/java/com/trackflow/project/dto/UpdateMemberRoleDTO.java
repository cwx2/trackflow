package com.trackflow.project.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新成员角色 DTO
 * 支持两种模式：
 * 1. 设置角色列表（替换所有角色）：使用 roleIds
 * 2. 单角色兼容模式：使用 roleId（向后兼容）
 */
@Data
public class UpdateMemberRoleDTO {

    /** 单角色（向后兼容） */
    private Long roleId;

    /** 多角色列表（全量替换） */
    private List<Long> roleIds;

    /**
     * 获取有效的角色ID列表
     */
    public List<Long> getEffectiveRoleIds() {
        if (roleIds != null && !roleIds.isEmpty()) {
            return roleIds;
        }
        if (roleId != null) {
            return List.of(roleId);
        }
        return List.of();
    }
}

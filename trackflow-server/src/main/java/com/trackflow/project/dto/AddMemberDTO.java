package com.trackflow.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AddMemberDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 单角色兼容字段（前端旧版本可能仍发送此字段）
     */
    private Long roleId;

    /**
     * 多角色字段（优先使用）
     */
    private List<Long> roleIds;

    /**
     * 获取有效的角色ID列表（兼容单角色和多角色）
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

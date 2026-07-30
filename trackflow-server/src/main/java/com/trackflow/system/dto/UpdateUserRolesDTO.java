package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量更新用户全局角色 DTO
 * 用于 PUT /api/v1/users/{id}/roles 端点
 * 
 * 语义：替换用户的全局角色集合
 * - 传入期望的完整角色 ID 列表
 * - 服务端计算差异后执行增删
 * - 空列表表示移除所有全局角色
 */
@Data
public class UpdateUserRolesDTO {
    
    /**
     * 期望的全局角色 ID 列表
     * 传入 null 或不传该字段会被校验拦截
     * 传入空列表 [] 表示清空所有全局角色
     */
    @NotNull(message = "角色列表不能为空")
    private List<Long> roleIds;
}

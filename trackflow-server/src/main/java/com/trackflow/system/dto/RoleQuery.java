package com.trackflow.system.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 角色列表查询条件封装
 * <p>
 * 供 RoleController.list 使用，替代多个松散的 @RequestParam。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQuery extends PageQuery {

    /** 角色类型过滤（global / project） */
    private String roleType;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "name", "code", "role_type", "created_at", "updated_at");
    }
}

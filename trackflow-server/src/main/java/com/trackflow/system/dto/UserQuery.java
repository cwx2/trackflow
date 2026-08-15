package com.trackflow.system.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 用户列表查询条件封装
 * <p>
 * 供 UserController.list 使用，替代多个松散的 @RequestParam，符合"超过 2 个查询参数必须封装 Query 对象"规范。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    /** 通用关键词（匹配用户名/姓名/邮箱） */
    private String keyword;

    /** 精确匹配用户名 */
    private String username;

    /** 精确匹配显示名称 */
    private String displayName;

    /** 精确匹配邮箱 */
    private String email;

    /** 按组织过滤 */
    private Long orgId;

    /** 账号状态过滤（active / disabled） */
    private String status;

    /** 封禁状态过滤 */
    private String banStatus;

    /** 按角色过滤 */
    private Long roleId;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "username", "display_name", "email", "status",
                "org_id", "created_at", "updated_at", "last_login_at");
    }
}

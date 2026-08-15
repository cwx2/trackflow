package com.trackflow.system.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 组织列表查询条件封装
 * <p>
 * 供 OrganizationController.list 使用，替代多个松散的 @RequestParam。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrgQuery extends PageQuery {

    /** 关键词（匹配组织名称/编码） */
    private String keyword;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "name", "code", "created_at", "updated_at");
    }
}

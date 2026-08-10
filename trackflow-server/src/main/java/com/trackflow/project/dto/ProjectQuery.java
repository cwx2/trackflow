package com.trackflow.project.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectQuery extends PageQuery {
    private String keyword;
    private String status;

    /**
     * 按权限过滤：仅返回用户在该项目中拥有指定权限的项目。
     * 例如 "sprint:create" 表示仅返回用户有 Sprint 创建权限的项目。
     * 为空时使用默认可见性逻辑（成员项目 + internal/public）。
     */
    private String requiredPermission;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of(
                "id", "name", "key", "status", "created_at", "updated_at"
        );
    }
}

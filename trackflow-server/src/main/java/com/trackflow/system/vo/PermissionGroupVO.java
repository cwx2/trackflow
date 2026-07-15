package com.trackflow.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 权限分组 VO - 按分类聚合的权限列表
 */
@Data
public class PermissionGroupVO {

    /**
     * 分类代码，如 'issue'
     */
    private String category;

    /**
     * 该分类下的权限列表
     */
    private List<PermissionVO> permissions;
}

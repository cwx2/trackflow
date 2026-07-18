package com.trackflow.customfield.dto;

import lombok.Data;

import java.util.List;

/**
 * 设置字段可见性和编辑权限的 DTO
 */
@Data
public class SetFieldVisibilityDTO {

    /**
     * 可以查看此字段的角色 ID 列表。
     * null 或空列表 = 所有项目成员可见。
     */
    private List<Long> visibleToRoles;

    /**
     * 可以编辑此字段的角色 ID 列表。
     * null 或空列表 = 所有可见此字段的用户都能编辑。
     */
    private List<Long> updatableByRoles;
}

package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 替换角色权限列表请求 DTO
 */
@Data
public class ReplacePermissionsDTO {

    @NotNull(message = "权限列表不能为空")
    private List<String> permissions;
}

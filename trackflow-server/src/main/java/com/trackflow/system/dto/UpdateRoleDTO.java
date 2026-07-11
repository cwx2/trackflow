package com.trackflow.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRoleDTO {

    @Size(max = 100, message = "角色名称不能超过100字符")
    private String name;

    private String description;
    private Integer sortOrder;
}

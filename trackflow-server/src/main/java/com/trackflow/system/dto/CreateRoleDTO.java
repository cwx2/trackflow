package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoleDTO {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100字符")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 50, message = "角色编码长度必须在2-50之间")
    private String code;

    private String description;

    @NotBlank(message = "角色类型不能为空")
    private String roleType;  // global / project
}

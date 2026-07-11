package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrgDTO {

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 200, message = "组织名称不能超过200字符")
    private String name;

    @NotBlank(message = "组织编码不能为空")
    @Size(min = 2, max = 50, message = "组织编码长度必须在2-50之间")
    private String code;

    @Size(max = 500, message = "描述不能超过500字符")
    private String description;
}

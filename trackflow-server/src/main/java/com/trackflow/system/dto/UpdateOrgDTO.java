package com.trackflow.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateOrgDTO {

    @Size(max = 200, message = "组织名称不能超过200字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500字符")
    private String description;
}

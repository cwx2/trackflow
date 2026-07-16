package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateApiKeyDTO {
    @NotBlank(message = "API Key名称不能为空")
    @Size(max = 100, message = "API Key名称不能超过100个字符")
    private String name;

    @Size(max = 20, message = "权限列表最多包含20项")
    private List<String> permissions;

    private String expiresAt;
}

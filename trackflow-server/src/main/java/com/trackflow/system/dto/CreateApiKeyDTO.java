package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class CreateApiKeyDTO {
    @NotBlank(message = "API Key名称不能为空")
    private String name;
    private List<String> permissions;
    private String expiresAt;
}

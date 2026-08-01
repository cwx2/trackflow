package com.trackflow.automation.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AutomationRoleProfileDTO {
    @NotBlank
    @Size(max = 100)
    private String name;
    @Size(max = 2000)
    private String description;
    @NotBlank
    private String providerType;
    private String model;
    private String systemPrompt;
    private String toolPolicy;
    private String outputSchema;
    private String workspacePolicy;
    private Boolean enabled;
}

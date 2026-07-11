package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRoleDTO {
    @NotNull
    private Long roleId;
}

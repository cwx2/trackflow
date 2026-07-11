package com.trackflow.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleDTO {
    @NotNull
    private Long roleId;
}

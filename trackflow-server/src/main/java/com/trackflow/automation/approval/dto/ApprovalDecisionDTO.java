package com.trackflow.automation.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApprovalDecisionDTO {
    @NotBlank
    private String decision;
    private String comment;
}

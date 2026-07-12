package com.trackflow.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateWorkflowDTO {
    @NotNull
    private String issueType;

    @NotNull
    private Long roleId;

    @NotNull
    @Valid
    private List<TransitionItem> transitions;

    @Data
    public static class TransitionItem {
        @NotNull
        private Long from;
        @NotNull
        private Long to;
        private Boolean allowed;
    }
}

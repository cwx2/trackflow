package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateWorkflowDTO {
    private String issueType;

    @NotNull
    private Long roleId;

    private List<TransitionItem> transitions;

    @Data
    public static class TransitionItem {
        private Long from;
        private Long to;
        private Boolean allowed;
    }
}

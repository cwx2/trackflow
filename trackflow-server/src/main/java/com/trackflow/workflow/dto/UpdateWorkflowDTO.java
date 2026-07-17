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

    /**
     * 此次更新针对的模式：
     * author=false, assignee=false → Normal 模式（基础规则）
     * author=true → Author 模式（创建者额外规则）
     * assignee=true → Assignee 模式（负责人额外规则）
     */
    private Boolean author;
    private Boolean assignee;

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

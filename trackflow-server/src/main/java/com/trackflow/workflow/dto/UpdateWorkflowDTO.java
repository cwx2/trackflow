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

    /**
     * 乐观锁版本号。从 GET 接口获取，PUT 时回传用于并发冲突检测。
     * null 表示客户端未传版本号（向后兼容，不做校验）。
     */
    private Integer version;

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
        /** 是否要求此转换必须附带评论/理由 */
        private Boolean requireComment;
    }
}

package com.trackflow.issue.vo;

import lombok.Data;

import java.util.List;

@Data
public class IssueStatusVO {
    private String id;
    private String name;
    private String displayName;
    private String code;
    private String color;
    private String category;
    private Boolean isDefault;
    private Boolean isClosed;
    private Integer sortOrder;

    /**
     * 是否被阻塞（当前 issue 有未解决的 blocker 且此状态为关闭状态时为 true）
     */
    private Boolean blocked;

    /**
     * 阻塞方的 issueKey 列表（如 ["DE4-1001", "DE4-1003"]）
     */
    private List<String> blockedBy;

    /**
     * 此转换是否要求必须填写评论/理由（来自 workflow_transition.require_comment 配置）
     */
    private Boolean requireComment;
}

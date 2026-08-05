package com.trackflow.issue.vo;

import lombok.Data;

@Data
public class IssueLinkVO {
    private String id;
    private String linkType;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private IssueStatusVO issueStatus;
    /** 优先级值（如 "Normal", "Critical"） */
    private String priority;
    /** 优先级对应颜色（HEX 格式） */
    private String priorityColor;
    /** 优先级排序序号（用于方块中显示的数字，从 1 开始） */
    private Integer priorityOrder;
}

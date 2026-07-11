package com.trackflow.issue.vo;

import lombok.Data;

@Data
public class IssueStatusVO {
    private String id;
    private String name;
    private String code;
    private String color;
    private String category;
    private Boolean isDefault;
    private Boolean isClosed;
    private Integer sortOrder;
}

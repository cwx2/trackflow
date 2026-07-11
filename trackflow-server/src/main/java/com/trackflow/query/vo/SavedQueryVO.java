package com.trackflow.query.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SavedQueryVO {
    private String id;
    private String name;
    private String projectId;
    private String userId;
    private Boolean shared;
    private Boolean pinned;
    private String folder;
    private String filters;
    private String columns;
    private String sortCriteria;
    private String groupBy;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

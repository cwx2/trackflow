package com.trackflow.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectVO {
    private String id;          // Long→String
    private String name;
    private String key;
    private String description;
    private String orgId;       // Long→String
    private String leadId;      // Long→String
    private String status;
    private String visibility;
    private Integer issueSequence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 项目成员总数 */
    private Integer memberCount;
    /** 前几名成员的显示名称（用于头像展示） */
    private List<String> topMembers;
    /** 当前用户是否已收藏该项目 */
    private Boolean favorited;
}

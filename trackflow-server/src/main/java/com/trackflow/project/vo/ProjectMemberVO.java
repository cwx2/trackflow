package com.trackflow.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectMemberVO {
    private String id;          // Long→String
    private String projectId;   // Long→String
    private String userId;      // Long→String
    private String roleId;      // Long→String
    private String username;
    private String displayName;
    private String email;
    private LocalDateTime joinedAt;
}

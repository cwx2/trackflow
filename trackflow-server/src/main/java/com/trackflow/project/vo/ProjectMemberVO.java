package com.trackflow.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectMemberVO {
    private String id;          // Long→String (first member record id)
    private String projectId;   // Long→String
    private String userId;      // Long→String
    /** 主角色ID（向后兼容，取第一个角色） */
    private String roleId;      // Long→String
    /** 所有角色ID列表 */
    private List<String> roleIds;
    /** 所有角色名称列表 */
    private List<String> roleNames;
    private String username;
    private String displayName;
    private String email;
    private LocalDateTime joinedAt;
}

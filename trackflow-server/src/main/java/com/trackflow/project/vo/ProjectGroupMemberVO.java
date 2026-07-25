package com.trackflow.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通过用户组获得项目访问权的成员信息
 */
@Data
public class ProjectGroupMemberVO {
    private String groupId;       // Long→String
    private String groupName;
    private String projectId;     // Long→String
    /** 组在该项目中的角色ID */
    private String roleId;        // Long→String
    /** 组在该项目中的角色名称 */
    private String roleName;
    /** 组成员列表 */
    private List<GroupUserVO> users;
    /** 组角色分配时间 */
    private LocalDateTime assignedAt;

    @Data
    public static class GroupUserVO {
        private String userId;       // Long→String
        private String username;
        private String displayName;
        private String email;
    }
}

package com.trackflow.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目活动日志 VO
 */
@Data
public class ProjectActivityVO {

    private String id;
    private String projectId;
    private String userId;
    private String action;
    private String targetUserId;
    private String detail;
    private LocalDateTime createdAt;

    // 关联的显示名称（由 Service 层填充）
    private String userName;        // 操作者姓名
    private String targetUserName;  // 被操作者姓名
}

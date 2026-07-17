package com.trackflow.integration.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationVO {
    private String id;
    private String userId;
    private String actorId;
    private String actorName;
    private String actorAvatar;
    private String title;
    private String content;
    private String type;
    private String resourceType;
    private String resourceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer aggregationCount;
    /** 该通知对应的资源是否已被当前用户静音 */
    private Boolean resourceMuted;
}

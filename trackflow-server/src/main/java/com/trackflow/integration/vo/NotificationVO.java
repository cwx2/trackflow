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
    private String projectId;
    private String title;
    private String content;
    private String type;
    private String reason;
    private String reasonLabel;
    private String resourceType;
    private String resourceId;
    private String resourceUrl;
    /** 来源子资源 ID（如评论 ID），前端用于构造 hash 锚点精准定位 */
    private String sourceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer aggregationCount;
    /** 资源标题（如工单标题），用于通知分组时显示 */
    private String resourceTitle;
    /** 该通知对应的资源是否已被当前用户静音 */
    private Boolean resourceMuted;
}

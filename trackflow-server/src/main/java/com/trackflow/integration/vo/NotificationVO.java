package com.trackflow.integration.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationVO {
    private String id;
    private String userId;
    private String title;
    private String content;
    private String type;
    private String resourceType;
    private String resourceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

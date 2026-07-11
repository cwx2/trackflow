package com.trackflow.integration.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WebhookVO {
    private String id;
    private String projectId;
    private String name;
    private String url;
    private String events;
    private Boolean active;
    private LocalDateTime createdAt;
}

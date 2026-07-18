package com.trackflow.integration.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WebhookLogVO {
    private String id;
    private String webhookId;
    private String event;
    private Integer responseStatus;
    private String responseBody;
    private Boolean success;
    private LocalDateTime createdAt;
}

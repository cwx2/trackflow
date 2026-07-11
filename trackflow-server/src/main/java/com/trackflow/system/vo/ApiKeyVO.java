package com.trackflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiKeyVO {
    private String id;
    private String userId;
    private String name;
    private String prefix;
    private String permissions;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}

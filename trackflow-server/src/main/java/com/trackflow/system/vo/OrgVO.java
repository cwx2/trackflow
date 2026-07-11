package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrgVO {
    private String id;
    private String name;
    private String code;
    private String description;
    private LocalDateTime createdAt;
}
